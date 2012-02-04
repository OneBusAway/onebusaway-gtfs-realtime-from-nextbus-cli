package org.onebusaway.gtfs_realtime.nextbus.services;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;

import org.onebusaway.collections.Max;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBRoute;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBStop;

@Singleton
public class NextBusToGtfsRouteMatching {

  public Map<NBRoute, Route> getRouteMatches(List<NBRoute> routes,
      GtfsRelationalDao dao, Map<NBStop, List<Stop>> potentialStopMatches) {
    Map<Route, Set<Stop>> stopsByRoute = getStopsByRoute(dao);
    Map<NBRoute, Route> routeMatches = new HashMap<NBRoute, Route>();

    for (NBRoute nbRoute : routes) {
      Max<Route> m = new Max<Route>();
      for (Map.Entry<Route, Set<Stop>> entry : stopsByRoute.entrySet()) {
        Route gtfsRoute = entry.getKey();
        Set<Stop> gtfsStops = entry.getValue();
        double hits = 0;
        for (NBStop nbStop : nbRoute.getStops()) {
          List<Stop> list = potentialStopMatches.get(nbStop);
          if (hasMatchingPotentialStop(gtfsStops, list)) {
            hits++;
          }
        }
        double ratio = hits / nbRoute.getStops().size();
        m.add(ratio, gtfsRoute);
      }
      routeMatches.put(nbRoute, m.getMaxElement());
    }
    return routeMatches;
  }

  private Map<Route, Set<Stop>> getStopsByRoute(GtfsRelationalDao dao) {
    Map<Route, Set<Stop>> stopsByRoute = new HashMap<Route, Set<Stop>>();
    for (Route route : dao.getAllRoutes()) {
      Set<Stop> stops = new HashSet<Stop>();
      for (Trip trip : dao.getTripsForRoute(route)) {
        for (StopTime stopTime : dao.getStopTimesForTrip(trip)) {
          stops.add(stopTime.getStop());
        }
      }
      stopsByRoute.put(route, stops);
    }
    return stopsByRoute;
  }

  private boolean hasMatchingPotentialStop(Set<Stop> gtfsStops, List<Stop> list) {
    if (list != null) {
      for (Stop potentialStop : list) {
        if (gtfsStops.contains(potentialStop)) {
          return true;
        }
      }
    }
    return false;
  }
}
