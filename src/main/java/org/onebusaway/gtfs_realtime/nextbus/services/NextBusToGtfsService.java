package org.onebusaway.gtfs_realtime.nextbus.services;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.gtfs_realtime.nextbus.model.FlatPrediction;
import org.onebusaway.gtfs_realtime.nextbus.model.RouteDirectionStopKey;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBRoute;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBStop;

/**
 * Attempt to match NextBus route, stop, and block tags to GTFS route, stop, and
 * trip ids.
 * 
 * @author bdferris
 */
@Singleton
public class NextBusToGtfsService {

  private NextBusToGtfsStopMatching _stopMatching;

  private NextBusToGtfsRouteMatching _routeMatching;

  private NextBusToGtfsTripMatching _tripMatching;

  private File _gtfsPath;

  private boolean _gtfsTripMatching;

  private ConcurrentMap<String, String> _routeIdMappings = new ConcurrentHashMap<String, String>();

  private ConcurrentMap<RouteDirectionStopKey, String> _stopIdMappings = new ConcurrentHashMap<RouteDirectionStopKey, String>();

  @Inject
  public void setStopMatching(NextBusToGtfsStopMatching stopMatching) {
    _stopMatching = stopMatching;
  }

  @Inject
  public void setRouteMatching(NextBusToGtfsRouteMatching routeMatching) {
    _routeMatching = routeMatching;
  }

  @Inject
  public void setTripMatching(NextBusToGtfsTripMatching tripMatching) {
    _tripMatching = tripMatching;
  }

  public void setGtfsPath(File path) {
    _gtfsPath = path;
  }

  public void setGtfsTripMatching(boolean gtfsTripMatching) {
    _gtfsTripMatching = gtfsTripMatching;
  }

  public synchronized void matchToGtfs(List<NBRoute> routes) {
    if (_gtfsPath == null) {
      return;
    }
    GtfsRelationalDao dao = readGtfs();

    Map<NBStop, List<Stop>> potentialStopMatches = _stopMatching.getPotentialStopMatches(
        routes, dao.getAllStops());

    Map<NBRoute, Route> routeMatches = _routeMatching.getRouteMatches(routes,
        dao, potentialStopMatches);

    _routeIdMappings.clear();
    for (Map.Entry<NBRoute, Route> entry : routeMatches.entrySet()) {
      NBRoute nbRoute = entry.getKey();
      Route gtfsRoute = entry.getValue();
      _routeIdMappings.put(nbRoute.getTag(), gtfsRoute.getId().getId());
    }

    Map<RouteDirectionStopKey, String> stopIdMappings = _stopMatching.getStopMatches(
        routeMatches, potentialStopMatches, dao);
    _stopIdMappings.clear();
    _stopIdMappings.putAll(stopIdMappings);

    if (_gtfsTripMatching) {
      _tripMatching.getTripMatches(routeMatches, stopIdMappings, dao);
    }
  }

  public String getMappingForRouteTag(String routeTag) {
    String updated = _routeIdMappings.get(routeTag);
    return updated == null ? routeTag : updated;
  }

  public String getMappingForRouteDirectionStopTag(String routeTag,
      String directionTag, String stopTag) {
    RouteDirectionStopKey key = new RouteDirectionStopKey(routeTag,
        directionTag, stopTag);
    String updated = _stopIdMappings.get(key);
    return updated == null ? routeTag : updated;
  }

  public void applyGtfsTripMapping(List<FlatPrediction> predictions) {

  }

  /****
   * Private Methods
   ****/

  private GtfsRelationalDao readGtfs() {
    try {

      GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();

      GtfsReader reader = new GtfsReader();
      reader.setInputLocation(_gtfsPath);
      reader.setEntityStore(dao);
      reader.run();

      return dao;

    } catch (IOException ex) {
      throw new IllegalStateException("error reading GTFS", ex);
    }
  }
}
