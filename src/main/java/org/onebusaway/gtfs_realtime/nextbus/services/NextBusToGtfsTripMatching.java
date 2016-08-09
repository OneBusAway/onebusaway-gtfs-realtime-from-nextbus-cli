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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.collections.MappingLibrary;
import org.onebusaway.collections.Min;
import org.onebusaway.collections.tuple.T2;
import org.onebusaway.collections.tuple.Tuples;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.gtfs.services.calendar.CalendarServiceDataFactory;
import org.onebusaway.gtfs_realtime.nextbus.model.FlatStopTime;
import org.onebusaway.gtfs_realtime.nextbus.model.RouteDirectionStopKey;
import org.onebusaway.gtfs_realtime.nextbus.model.ServiceDateBlockKey;
import org.onebusaway.gtfs_realtime.nextbus.model.StopTimeIndices;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBRoute;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBStopTime;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBTrip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NextBusToGtfsTripMatching {

  private static final Logger _log = LoggerFactory.getLogger(NextBusToGtfsTripMatching.class);

  private static final Map<String, String> _serviceClassToDaymask = new HashMap<String, String>();

  private int _tripIndex = 0;

  static {
    _serviceClassToDaymask.put("mtwth", "1111000");
    _serviceClassToDaymask.put("f", "0000100");
    _serviceClassToDaymask.put("sat", "0000010");
    _serviceClassToDaymask.put("sun", "0000001");
    _serviceClassToDaymask.put("MoTuWeTh", "1111000");
    _serviceClassToDaymask.put("Friday", "0000100");
    _serviceClassToDaymask.put("Saturday", "0000010");
    _serviceClassToDaymask.put("Sunday", "0000001");
  }

  private NextBusApiService _nextBusApiServie;

  @Inject
  public void setNextBusApiService(NextBusApiService nextBusApiService) {
    _nextBusApiServie = nextBusApiService;
  }

  public Map<ServiceDateBlockKey, StopTimeIndices> getTripMatches(
      Map<NBRoute, Route> routeMatches,
      Map<RouteDirectionStopKey, String> stopIdMappings, GtfsRelationalDao dao) {

    Map<ServiceDateBlockKey, StopTimeIndices> mappings = new HashMap<ServiceDateBlockKey, StopTimeIndices>();

    CalendarServiceDataFactory factory = new CalendarServiceDataFactoryImpl(dao);
    CalendarServiceData data = factory.createData();

    for (Map.Entry<NBRoute, Route> entry : routeMatches.entrySet()) {
      NBRoute nbRoute = entry.getKey();
      Route gtfsRoute = entry.getValue();
      List<NBRoute> schedules = getSchedulesForRoute(nbRoute);
      Map<String, List<AgencyAndId>> serviceIdsByServiceClass = getApplicableServiceIdsForByServiceClass(
          dao, gtfsRoute, schedules);
      Map<String, List<List<StopTime>>> tripStopTimesByServiceClass = computeTripStopTimesByServiceClass(
          dao, gtfsRoute, serviceIdsByServiceClass);

      List<FlatStopTime> stopTimes = flattenSchedules(schedules, stopIdMappings);
      Map<String, List<FlatStopTime>> stopTimesByScheduleClass = MappingLibrary.mapToValueList(
          stopTimes, "scheduleClass");

      for (Map.Entry<String, List<FlatStopTime>> scheduleClassEntry : stopTimesByScheduleClass.entrySet()) {

        List<FlatStopTime> stopTimesForScheduleClass = scheduleClassEntry.getValue();
        Map<String, List<FlatStopTime>> stopTimesByServiceClass = MappingLibrary.mapToValueList(
            stopTimesForScheduleClass, "serviceClass");

        for (Map.Entry<String, List<FlatStopTime>> serviceClassEntry : stopTimesByServiceClass.entrySet()) {
          String serviceClass = serviceClassEntry.getKey();
          List<AgencyAndId> serviceIds = serviceIdsByServiceClass.get(serviceClass);

          Min<T2<AgencyAndId, Map<String, StopTimeIndices>>> m = new Min<T2<AgencyAndId, Map<String, StopTimeIndices>>>();

          for (AgencyAndId serviceId : serviceIds) {

            List<FlatStopTime> stopTimesForServiceClass = serviceClassEntry.getValue();
            List<List<StopTime>> gtfsStopTimesByTrip = tripStopTimesByServiceClass.get(serviceClass);
            Map<String, StopTimeIndices> stopTimeIndices = new HashMap<String, StopTimeIndices>();

            double score = findBestStopTimeIndicesForNextBusBlocks(
                stopTimesForServiceClass, gtfsStopTimesByTrip, stopTimeIndices);
            m.add(score, Tuples.tuple(serviceId, stopTimeIndices));
          }

          T2<AgencyAndId, Map<String, StopTimeIndices>> best = m.getMinElement();
          AgencyAndId serviceId = best.getFirst();
          Map<String, StopTimeIndices> stopTimeIndicesByBlockId = best.getSecond();
          List<ServiceDate> serviceDates = data.getServiceDatesForServiceId(serviceId);
          for (ServiceDate serviceDate : serviceDates) {
            for (Map.Entry<String, StopTimeIndices> gentry : stopTimeIndicesByBlockId.entrySet()) {
              mappings.put(new ServiceDateBlockKey(gtfsRoute.getId().getId(),
                  gentry.getKey(), serviceDate), gentry.getValue());
            }
          }
        }
      }
    }

    return mappings;
  }

  private double findBestStopTimeIndicesForNextBusBlocks(
      List<FlatStopTime> stopTimesForServiceClass,
      List<List<StopTime>> gtfsStopTimesByTrip,
      Map<String, StopTimeIndices> resultingStopTimeIndicesByBlockid) {

    Map<String, List<FlatStopTime>> stopTimesByBlock = MappingLibrary.mapToValueList(
        stopTimesForServiceClass, "blockTag");

    double score = 0;

    for (Map.Entry<String, List<FlatStopTime>> blockEntry : stopTimesByBlock.entrySet()) {
      List<FlatStopTime> stopTimesForBlock = blockEntry.getValue();

      fixTripGroupingsForBlock(stopTimesForBlock);

      Map<Integer, List<FlatStopTime>> stopTimesByTrip = MappingLibrary.mapToValueList(
          stopTimesForBlock, "tripIndex");

      List<StopTime> bestStopTimesForBlock = new ArrayList<StopTime>();

      List<List<FlatStopTime>> stopTimesSortedByTrip = new ArrayList<List<FlatStopTime>>(
          stopTimesByTrip.values());
      Collections.sort(stopTimesSortedByTrip, new FlatStopTimeListComparator());

      for (List<FlatStopTime> stopTimesForTrip : stopTimesSortedByTrip) {
        score += findBestGtfsTripForNextBusTrip(stopTimesForTrip,
            gtfsStopTimesByTrip, bestStopTimesForBlock);
      }
      Collections.sort(bestStopTimesForBlock);
      StopTimeIndices indices = StopTimeIndices.create(bestStopTimesForBlock);
      resultingStopTimeIndicesByBlockid.put(blockEntry.getKey(), indices);
    }

    return score;
  }

  private double findBestGtfsTripForNextBusTrip(List<FlatStopTime> nextBusTrip,
      List<List<StopTime>> gtfsStopTimesByTrip,
      List<StopTime> bestStopTimesForBlock) {

    Collections.sort(nextBusTrip);

    Min<List<StopTime>> m = new Min<List<StopTime>>();
    for (List<StopTime> gtfsTrip : gtfsStopTimesByTrip) {
      double score = computeStopTimeAlignmentScore(nextBusTrip, gtfsTrip);
      m.add(score, gtfsTrip);
    }

    if (m.getMinValue() > 2 * 60) {
      StringBuilder b = new StringBuilder();
      for (FlatStopTime stopTime : nextBusTrip) {
        b.append("\n  ");
        b.append(stopTime.getRouteTag());
        b.append(" ");
        b.append(stopTime.getScheduleClass());
        b.append(" ");
        b.append(stopTime.getServiceClass());
        b.append(" ");
        b.append(stopTime.getDirectionTag());
        b.append(" ");
        b.append(stopTime.getBlockTag());
        b.append(" ");
        b.append(stopTime.getStopTag());
        b.append(" ");
        b.append(stopTime.getEpochTimeAsString());

      }
      _log.warn("no good match found for trip:" + b.toString());
    } else {
      List<StopTime> bestStopTimes = m.getMinElement();
      bestStopTimesForBlock.addAll(bestStopTimes);

    }
    return m.getMinValue();
  }

  private List<NBRoute> getSchedulesForRoute(NBRoute nbRoute) {
    try {
      return _nextBusApiServie.downloadRouteScheduleList(nbRoute.getTag());
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private List<FlatStopTime> flattenSchedules(List<NBRoute> schedules,
      Map<RouteDirectionStopKey, String> stopIdMappings) {
    List<FlatStopTime> flattened = new ArrayList<FlatStopTime>();
    for (NBRoute schedule : schedules) {
      List<NBTrip> trips = schedule.getTrips();
      for (NBTrip trip : trips) {
        for (NBStopTime stopTime : trip.getStopTimes()) {

          /**
           * Skip stop times that have an unspecified epoch time.
           */
          if (stopTime.getEpochTime() < 0) {
            continue;
          }

          FlatStopTime flat = new FlatStopTime();
          flat.setBlockTag(trip.getBlockID());
          flat.setDirectionTag(schedule.getDirection());
          flat.setEpochTime(stopTime.getEpochTime());
          flat.setRouteTag(schedule.getTag());
          flat.setScheduleClass(schedule.getScheduleClass());
          flat.setServiceClass(schedule.getServiceClass());
          flat.setStopTag(stopTime.getTag());
          flat.setTripIndex(_tripIndex);

          RouteDirectionStopKey key = new RouteDirectionStopKey(
              flat.getRouteTag(), flat.getDirectionTag(), flat.getStopTag());
          String gtfsStopId = stopIdMappings.get(key);
          flat.setGtfsStopId(gtfsStopId);

          flattened.add(flat);
        }
        _tripIndex++;
      }
    }
    return flattened;
  }

  private Map<String, List<AgencyAndId>> getApplicableServiceIdsForByServiceClass(
      GtfsRelationalDao dao, Route gtfsRoute, List<NBRoute> schedules) {
    Set<String> serviceClasses = new HashSet<String>();
    for (NBRoute schedule : schedules) {
      serviceClasses.add(schedule.getServiceClass());
    }

    Map<String, List<AgencyAndId>> serviceIdsByServiceClass = new HashMap<String, List<AgencyAndId>>();

    for (String serviceClass : serviceClasses) {
      List<AgencyAndId> serviceIds = getApplicableServiceIdsForServiceClass(
          serviceClass, dao);
      serviceIdsByServiceClass.put(serviceClass, serviceIds);
    }
    return serviceIdsByServiceClass;
  }

  private Map<String, List<List<StopTime>>> computeTripStopTimesByServiceClass(
      GtfsRelationalDao dao, Route gtfsRoute,
      Map<String, List<AgencyAndId>> serviceIdsByServiceClass) {

    Map<String, List<List<StopTime>>> tripStopTimesByServiceClass = new HashMap<String, List<List<StopTime>>>();

    Map<AgencyAndId, List<Trip>> tripsByServiceId = MappingLibrary.mapToValueList(
        dao.getTripsForRoute(gtfsRoute), "serviceId");

    for (Map.Entry<String, List<AgencyAndId>> entry : serviceIdsByServiceClass.entrySet()) {
      String serviceClass = entry.getKey();
      List<AgencyAndId> serviceIds = entry.getValue();
      List<List<StopTime>> allStopTimes = new ArrayList<List<StopTime>>();
      for (AgencyAndId serviceId : serviceIds) {
        List<Trip> trips = tripsByServiceId.get(serviceId);
        if (trips != null) {
          for (Trip trip : trips) {
            List<StopTime> tripStopTimes = dao.getStopTimesForTrip(trip);
            tripStopTimes = excludeUnspecifiedStopTimes(tripStopTimes);
            if (!tripStopTimes.isEmpty()) {
              allStopTimes.add(tripStopTimes);
            }
          }
        }
      }
      Collections.sort(allStopTimes, new StopTimeListComparator());
      tripStopTimesByServiceClass.put(serviceClass, allStopTimes);
    }

    return tripStopTimesByServiceClass;
  }

  private List<StopTime> excludeUnspecifiedStopTimes(
      List<StopTime> tripStopTimes) {
    List<StopTime> specified = new ArrayList<StopTime>();
    for (StopTime stopTime : tripStopTimes) {
      if (stopTime.isArrivalTimeSet() && stopTime.isDepartureTimeSet()) {
        specified.add(stopTime);
      }
    }
    return specified;
  }

  private List<AgencyAndId> getApplicableServiceIdsForServiceClass(
      String serviceClass, GtfsRelationalDao dao) {
    String daymask = _serviceClassToDaymask.get(serviceClass);
    if (daymask == null) {
      throw new IllegalStateException("unknown schedule serviceClass "
          + serviceClass);
    }
    Min<AgencyAndId> m = new Min<AgencyAndId>();
    for (ServiceCalendar c : dao.getAllCalendars()) {
      String gtfsDaymask = getCalendarAsDaymask(c);
      int matches = computeDaymaskOverlap(daymask, gtfsDaymask);
      m.add(-matches, c.getServiceId());
    }
    return m.getMinElements();
  }

  private String getCalendarAsDaymask(ServiceCalendar c) {
    StringBuilder b = new StringBuilder();
    b.append(c.getMonday());
    b.append(c.getTuesday());
    b.append(c.getWednesday());
    b.append(c.getThursday());
    b.append(c.getFriday());
    b.append(c.getSaturday());
    b.append(c.getSunday());
    return b.toString();
  }

  private int computeDaymaskOverlap(String a, String b) {
    int count = 0;
    for (int i = 0; i < a.length(); ++i) {
      if (a.charAt(i) == '1' && b.charAt(i) == '1') {
        count++;
      }
    }
    return count;
  }

  /**
   * The schedule timetable information returned by the nextbus API has a
   * strange tendency to return the columns out-of-order. This method attempts
   * to fix this by re-sorting the stop times for a block by time, and then
   * regrouping into trips based on the directionTag.
   * 
   * @param stopTimesForBlock
   */
  private void fixTripGroupingsForBlock(List<FlatStopTime> stopTimesForBlock) {
    Collections.sort(stopTimesForBlock);
    String prevDirection = null;
    for (FlatStopTime stopTime : stopTimesForBlock) {
      if (prevDirection == null
          || !prevDirection.equals(stopTime.getDirectionTag())) {
        prevDirection = stopTime.getDirectionTag();
        _tripIndex++;
      }
      stopTime.setTripIndex(_tripIndex);
    }
  }

  private double computeStopTimeAlignmentScore(List<FlatStopTime> nbStopTimes,
      List<StopTime> gtfsStopTimes) {

    Map<String, StopTimes> gtfsStopIdToStopTimes = new HashMap<String, StopTimes>();
    for (int index = 0; index < gtfsStopTimes.size(); index++) {
      StopTime stopTime = gtfsStopTimes.get(index);
      String stopId = stopTime.getStop().getId().getId();
      StopTimes stopTimes = gtfsStopIdToStopTimes.get(stopId);
      if (stopTimes == null) {
        stopTimes = new StopTimes();
        gtfsStopIdToStopTimes.put(stopId, stopTimes);
      }
      stopTimes.addStopTime(stopTime, index);
    }

    for (StopTimes stopTimes : gtfsStopIdToStopTimes.values()) {
      stopTimes.pack();
    }

    Map<FlatStopTime, Integer> mapping = new HashMap<FlatStopTime, Integer>();

    for (FlatStopTime nbStopTime : nbStopTimes) {
      StopTimes stopTimes = gtfsStopIdToStopTimes.get(nbStopTime.getGtfsStopId());
      if (stopTimes == null) {
        mapping.put(nbStopTime, -1);
      } else {
        int bestIndex = stopTimes.computeBestStopTimeIndex(nbStopTime.getEpochTime() / 1000);
        mapping.put(nbStopTime, bestIndex);
      }
    }

    int lastIndex = -1;
    int score = 0;
    boolean allMisses = true;

    for (Map.Entry<FlatStopTime, Integer> entry : mapping.entrySet()) {
      FlatStopTime nbStopTime = entry.getKey();
      int index = entry.getValue();
      StopTime gtfsStopTime = null;
      if (0 <= index && index < gtfsStopTimes.size()) {
        gtfsStopTime = gtfsStopTimes.get(index);
      }

      if (gtfsStopTime == null) {
        score += 15; // A miss is a 15 minute penalty
      } else {
        allMisses = false;
        if (index < lastIndex) {
          score += 15; // Out of order is a 10 minute penalty
        }
        int delta = Math.abs(nbStopTime.getEpochTime() / 1000
            - getTime(gtfsStopTime)) / 60;
        score += delta;
        lastIndex = index;
      }
    }

    if (allMisses)
      return 4 * 60 * 60;
    return score;
  }

  private static int getTime(StopTime stopTime) {
    return (stopTime.getDepartureTime() + stopTime.getArrivalTime()) / 2;
  }

  private static class StopTimes {
    private List<StopTime> stopTimes = new ArrayList<StopTime>();
    private List<Integer> indices = new ArrayList<Integer>();
    private int[] times;

    public void addStopTime(StopTime stopTime, int index) {
      stopTimes.add(stopTime);
      indices.add(index);
    }

    public int computeBestStopTimeIndex(int time) {
      int index = Arrays.binarySearch(times, time);
      if (index < 0) {
        index = -(index + 1);
      }
      if (index < 0 || index >= indices.size()) {
        return -1;
      }
      return indices.get(index);
    }

    public void pack() {
      times = new int[stopTimes.size()];
      for (int i = 0; i < stopTimes.size(); ++i) {
        StopTime stopTime = stopTimes.get(i);
        times[i] = getTime(stopTime);
      }
    }
  }

  private static class FlatStopTimeListComparator implements
      Comparator<List<FlatStopTime>> {

    @Override
    public int compare(List<FlatStopTime> o1, List<FlatStopTime> o2) {
      FlatStopTime stopTime1 = o1.get(0);
      FlatStopTime stopTime2 = o2.get(0);
      return stopTime1.getEpochTime() - stopTime2.getEpochTime();
    }

  }
  private static class StopTimeListComparator implements
      Comparator<List<StopTime>> {

    @Override
    public int compare(List<StopTime> o1, List<StopTime> o2) {
      StopTime stopTime1 = o1.get(0);
      StopTime stopTime2 = o2.get(0);
      return stopTime1.getDepartureTime() - stopTime2.getDepartureTime();
    }
  }
}
