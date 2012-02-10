/**
 * Copyright (C) 2012 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.gtfs_realtime.nextbus.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;

import org.onebusaway.collections.MappingLibrary;
import org.onebusaway.collections.Min;
import org.onebusaway.geospatial.model.CoordinateBounds;
import org.onebusaway.geospatial.services.SphericalGeometryLibrary;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.gtfs_realtime.nextbus.model.RouteDirectionStopKey;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBDirection;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBRoute;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBStop;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.index.strtree.STRtree;

@Singleton
public class NextBusToGtfsStopMatching {

  private double _stopMatchingDistanceThreshold = 75;

  public void setStopMatchingThreshold(double stopMatchingThreshold) {
    _stopMatchingDistanceThreshold = stopMatchingThreshold;
  }

  @SuppressWarnings("unchecked")
  public Map<NBStop, List<Stop>> getPotentialStopMatches(
      List<NBRoute> nbRoutes, Collection<Stop> gtfsStops) {

    Map<String, NBStop> nbStopsByTag = getStopsByTag(nbRoutes);

    STRtree tree = new STRtree(gtfsStops.size());
    for (Stop stop : gtfsStops) {
      tree.insert(new Envelope(new Coordinate(stop.getLon(), stop.getLat())),
          stop);
    }
    tree.build();

    Map<NBStop, List<Stop>> potentialMatches = new HashMap<NBStop, List<Stop>>();
    for (NBStop nbStop : nbStopsByTag.values()) {
      CoordinateBounds b = SphericalGeometryLibrary.bounds(nbStop.getLat(),
          nbStop.getLon(), _stopMatchingDistanceThreshold);
      Envelope env = new Envelope(b.getMinLon(), b.getMaxLon(), b.getMinLat(),
          b.getMaxLat());
      List<Stop> stopsInEnvelope = tree.query(env);
      potentialMatches.put(nbStop, stopsInEnvelope);
    }

    return potentialMatches;
  }

  public Map<RouteDirectionStopKey, String> getStopMatches(
      Map<NBRoute, Route> routeMatches,
      Map<NBStop, List<Stop>> potentialStopMatches, GtfsRelationalDao dao) {

    Map<RouteDirectionStopKey, String> stopIdMappings = new HashMap<RouteDirectionStopKey, String>();

    for (Map.Entry<NBRoute, Route> entry : routeMatches.entrySet()) {

      NBRoute nbRoute = entry.getKey();
      Route gtfsRoute = entry.getValue();

      Set<List<Stop>> stopSequences = getStopSequencesForRoute(dao, gtfsRoute);

      List<Map<Stop, Integer>> stopSequenceIndices = new ArrayList<Map<Stop, Integer>>();
      for (List<Stop> stopSequence : stopSequences) {
        Map<Stop, Integer> index = new HashMap<Stop, Integer>();
        for (int i = 0; i < stopSequence.size(); ++i) {
          index.put(stopSequence.get(i), i);
        }
        stopSequenceIndices.add(index);
      }

      for (NBDirection direction : nbRoute.getDirections()) {

        List<Match> matches = new ArrayList<Match>();
        for (NBStop fromStop : direction.getStops()) {
          List<Stop> toStops = potentialStopMatches.get(fromStop);
          Match m = new Match(fromStop, toStops);
          matches.add(m);
        }

        Min<Assignment> m = new Min<Assignment>();
        Assignment assignment = new Assignment(direction.getStops(),
            stopSequenceIndices);
        matches = applyDirectMatchesToAssignment(matches, assignment);
        recursivelyBuildAndScoreAssignment(matches, 0, assignment, m);
        Assignment bestAssignment = m.getMinElement();

        for (NBStop stop : direction.getStops()) {
          String stopId = bestAssignment._stops.get(stop).getId().getId();
          RouteDirectionStopKey key = new RouteDirectionStopKey(
              nbRoute.getTag(), direction.getTag(), stop.getTag());
          stopIdMappings.put(key, stopId);
        }
      }
    }
    return stopIdMappings;
  }

  /****
   * Private Methods
   ****/

  private Map<String, NBStop> getStopsByTag(List<NBRoute> routes) {
    Map<String, NBStop> stopsByTag = new HashMap<String, NBStop>();
    for (NBRoute route : routes) {
      for (NBStop stop : route.getStops()) {
        stopsByTag.put(stop.getTag(), stop);
      }
    }
    return stopsByTag;
  }

  private Set<List<Stop>> getStopSequencesForRoute(GtfsRelationalDao dao,
      Route route) {
    List<Trip> trips = dao.getTripsForRoute(route);
    Set<List<Stop>> sequences = new HashSet<List<Stop>>();
    for (Trip trip : trips) {
      List<StopTime> stopTimes = dao.getStopTimesForTrip(trip);
      List<Stop> stops = MappingLibrary.map(stopTimes, "stop");
      sequences.add(stops);
    }
    return sequences;
  }

  private List<Match> applyDirectMatchesToAssignment(List<Match> matches,
      Assignment assignment) {
    List<Match> remainingMatches = new ArrayList<Match>();
    for (Match match : matches) {
      if (match.to.size() == 1) {
        assignment.applyMatch(match, 0);
      } else {
        remainingMatches.add(match);
      }
    }
    return remainingMatches;
  }

  private void recursivelyBuildAndScoreAssignment(List<Match> matches,
      int depth, Assignment assignment, Min<Assignment> m) {
    if (depth == matches.size()) {
      double score = scoreAssignment(assignment);
      if (score < m.getMinValue()) {
        m.add(score, new Assignment(assignment));
      }

    } else {
      Match match = matches.get(depth);
      for (int i = 0; i < match.to.size(); ++i) {
        assignment.applyMatch(match, i);
        recursivelyBuildAndScoreAssignment(matches, depth + 1, assignment, m);
      }
    }
  }

  private double scoreAssignment(Assignment assignment) {

    List<Stop> stopsInOrder = new ArrayList<Stop>();
    for (NBStop stop : assignment._stopsInOrder) {
      stopsInOrder.add(assignment._stops.get(stop));
    }

    int min = Integer.MAX_VALUE;

    for (Map<Stop, Integer> indices : assignment._stopSequenceIndices) {
      int score = 0;
      int lastIndex = -1;
      for (Stop stop : stopsInOrder) {
        if (indices.containsKey(stop)) {
          int index = indices.get(stop);
          /**
           * We penalize for out-of-order stop sequences
           */
          if (index < lastIndex)
            score++;
          lastIndex = index;
        } else {
          /**
           * We also penalize for unmatched stops
           */
          score++;
        }
      }

      min = Math.min(score, min);
    }

    return min;
  }

  private static class Match {
    public final NBStop from;
    public final List<Stop> to;

    public Match(NBStop fromStop, List<Stop> toStops) {
      this.from = fromStop;
      this.to = toStops;
    }

    @Override
    public String toString() {
      return from.getTag() + " " + to;
    }
  }

  private static class Assignment {

    public final List<NBStop> _stopsInOrder;

    private final List<Map<Stop, Integer>> _stopSequenceIndices;

    private Map<NBStop, Stop> _stops = new HashMap<NBStop, Stop>();

    public Assignment(List<NBStop> stopsInOrder,
        List<Map<Stop, Integer>> stopSequenceIndices) {
      _stopsInOrder = stopsInOrder;
      _stopSequenceIndices = stopSequenceIndices;
    }

    public Assignment(Assignment o) {
      _stopsInOrder = o._stopsInOrder;
      _stopSequenceIndices = o._stopSequenceIndices;
      _stops.putAll(o._stops);
    }

    public void applyMatch(Match match, int index) {
      NBStop from = match.from;
      Stop to = match.to.get(index);
      _stops.put(from, to);
    }

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      for (NBStop stop : _stopsInOrder) {
        b.append(_stops.get(stop).getId().getId()).append(" ");
      }
      return b.toString();
    }
  }
}
