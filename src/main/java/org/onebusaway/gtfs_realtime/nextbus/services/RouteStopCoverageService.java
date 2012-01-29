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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.digester.Digester;
import org.onebusaway.collections.Counter;
import org.onebusaway.gtfs_realtime.nextbus.model.RouteStopCoverage;
import org.onebusaway.gtfs_realtime.nextbus.model.api.Direction;
import org.onebusaway.gtfs_realtime.nextbus.model.api.Route;
import org.onebusaway.gtfs_realtime.nextbus.model.api.Stop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * This class periodically queries the configuration information for all know
 * routes and stops for a particular agency to determine the set of stops we'll
 * be requesting real-time information about. The set of stops are selected in
 * order to (a) give good coverage along a route while (b) not overwhelming the
 * NextBus API with too many requests.
 * 
 * The route configuration information is periodically refreshed every morning
 * at 4am.
 * 
 * @author bdferris
 * 
 */
@Singleton
public class RouteStopCoverageService {

  private static final Logger _log = LoggerFactory.getLogger(RouteStopCoverageService.class);

  private DownloaderService _downloader;

  private ScheduledExecutorService _executor;

  private String _agencyId;

  private File _routeConfigurationCachePath;

  private volatile List<RouteStopCoverage> _routeStopCoverage = Collections.emptyList();

  private final RefreshTask _refreshTask = new RefreshTask();

  private ScheduledFuture<?> _refreshTaskInstance;

  @Inject
  public void setDownloader(DownloaderService downloader) {
    _downloader = downloader;
  }

  @Inject
  public void setExecutor(ScheduledExecutorService executor) {
    _executor = executor;
  }

  public void setAgencyId(String agencyId) {
    _agencyId = agencyId;
  }

  public void setRouteConfigurationCachePath(File routeConfigurationCachePath) {
    _routeConfigurationCachePath = routeConfigurationCachePath;
  }

  public List<RouteStopCoverage> getRouteStopCoverage() {
    return _routeStopCoverage;
  }

  @PostConstruct
  public void start() throws Exception {
    refreshRouteStopCoverage(true);
    scheduleNextRefresh();
  }

  @PreDestroy
  public void stop() {
    if (_refreshTaskInstance != null) {
      _refreshTaskInstance.cancel(true);
      _refreshTaskInstance = null;
    }
  }

  private void scheduleNextRefresh() {
    /**
     * We refresh the route config every morning at 4 am
     */
    Calendar c = Calendar.getInstance();
    c.add(Calendar.DAY_OF_YEAR, 1);
    c.set(Calendar.HOUR_OF_DAY, 4);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    long nextRefreshDelay = c.getTimeInMillis() - System.currentTimeMillis();

    _refreshTaskInstance = _executor.schedule(_refreshTask, nextRefreshDelay,
        TimeUnit.MILLISECONDS);
  }

  private synchronized void refreshRouteStopCoverage(boolean useCacheIfAvailable)
      throws IOException, SAXException, ClassNotFoundException {
    _log.info("Rebuilding route-stop coverage model");
    List<Route> routeConfigurations = readRouteConfigurations(useCacheIfAvailable);
    _routeStopCoverage = getRouteStopCoverageForRouteConfigurations(routeConfigurations);
  }

  @SuppressWarnings("unchecked")
  private List<Route> readRouteConfigurations(boolean useCacheIfAvailable)
      throws IOException, SAXException, ClassNotFoundException {
    if (useCacheIfAvailable && _routeConfigurationCachePath != null
        && _routeConfigurationCachePath.exists()) {
      ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
          new FileInputStream(_routeConfigurationCachePath)));
      Object object = ois.readObject();
      ois.close();
      return (List<Route>) object;
    }
    List<Route> routeConfigurations = downloadRouteConfigurations();
    if (_routeConfigurationCachePath != null) {
      ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(
          new FileOutputStream(_routeConfigurationCachePath)));
      oos.writeObject(routeConfigurations);
      oos.close();
    }
    return routeConfigurations;
  }

  private List<Route> downloadRouteConfigurations() throws IOException,
      SAXException {
    Digester digester = getRouteDigester();
    List<Route> routes = downloadRouteList(digester);
    List<Route> routeConfigurations = new ArrayList<Route>();
    int routeIndex = 0;
    for (Route route : routes) {
      _log.info("routes processed=" + (++routeIndex) + "/" + routes.size());
      List<Route> routeConfigs = downloadRouteConfigList(digester,
          route.getTag());
      routeConfigurations.addAll(routeConfigs);
    }
    return routeConfigurations;
  }

  @SuppressWarnings("unchecked")
  private List<Route> downloadRouteList(Digester digester) throws IOException,
      SAXException {
    InputStream in = _downloader.openUrl("http://webservices.nextbus.com/service/publicXMLFeed?command=routeList&a="
        + _agencyId);
    return (List<Route>) digester.parse(in);
  }

  @SuppressWarnings("unchecked")
  private List<Route> downloadRouteConfigList(Digester digester, String routeTag)
      throws IOException, SAXException {
    InputStream in = _downloader.openUrl("http://webservices.nextbus.com/service/publicXMLFeed?command=routeConfig&a="
        + _agencyId + "&r=" + routeTag);
    return (List<Route>) digester.parse(in);
  }

  private Digester getRouteDigester() {
    Digester digester = new Digester();
    digester.addObjectCreate("body", ArrayList.class);

    digester.addObjectCreate("body/route", Route.class);
    digester.addSetProperties("body/route");
    digester.addSetNext("body/route", "add");

    digester.addObjectCreate("body/route/stop", Stop.class);
    digester.addSetProperties("body/route/stop");
    digester.addSetNext("body/route/stop", "addStop");

    digester.addObjectCreate("body/route/direction", Direction.class);
    digester.addSetProperties("body/route/direction");
    digester.addSetNext("body/route/direction", "addDirection");

    digester.addObjectCreate("body/route/direction/stop", Stop.class);
    digester.addSetProperties("body/route/direction/stop");
    digester.addSetNext("body/route/direction/stop", "addStop");

    return digester;
  }

  private List<RouteStopCoverage> getRouteStopCoverageForRouteConfigurations(
      List<Route> routeConfigurations) {
    double downsampleRatio = computeDownsampleRatioForRouteConfigurations(routeConfigurations);
    List<RouteStopCoverage> coverage = new ArrayList<RouteStopCoverage>();
    for (Route route : routeConfigurations) {
      RouteStopCoverage routeStopCoverage = computeRouteStopCoverageForRoute(
          route, downsampleRatio);
      coverage.add(routeStopCoverage);
    }
    return coverage;
  }

  /**
   * In an ideal world, we'd request next-departure information for each stop
   * along a route configuration when querying the route. However, this is not
   * necessary in practice because (a) we'd quickly saturate the API bandwidth
   * quota and (b) much of the schedule deviation information is redundant from
   * one stop to the next.
   * 
   * So instead of requesting information about EVERY stop along a route, we'll
   * pick a subset of stops as determined by a downsample ratio. The downsample
   * ratio is the ratio of the number of stops we'll actually request info for
   * out of the total number of stops. We try to optimize the ratio to get a
   * good selection of stops while at the same time not saturating our API
   * bandwidth quota.
   * 
   * Right now, we pick the downsample ratio in the following way. First, we
   * assume that we'll make approximately one request per second and attempt to
   * make all our requests in 30 seconds, so that gives us a total of 30
   * requests. Next, we'll request information for 100 stops in each request.
   * That gives us a total of 3000 stops that we can request information for in
   * one cycle. As result, our downsample ratio is (3000 / the total number of
   * stops in our route configs).
   * 
   * @param routeConfigurations
   * @return
   */
  private double computeDownsampleRatioForRouteConfigurations(
      List<Route> routeConfigurations) {
    /**
     * Here, segment count is roughly analogous to the number of unique stops
     * per route.
     */
    int segmentCount = getSegmentCountForRoutes(routeConfigurations);
    double downsampleRatio = (30.0 /* requests */* 100 /* stops per request */)
        / segmentCount;
    /**
     * We never want to include more than half the stops
     */
    downsampleRatio = Math.min(0.5, downsampleRatio);
    return downsampleRatio;
  }

  private RouteStopCoverage computeRouteStopCoverageForRoute(Route route,
      double downsampleRatio) {
    Set<String> stopTags = new HashSet<String>();
    /**
     * We add the trip ends no matter what
     */
    for (Direction direction : route.getDirections()) {
      List<Stop> stops = direction.getStops();
      Stop lastStop = stops.get(stops.size() - 1);
      stopTags.add(lastStop.getTag());
    }
    int segmentCount = getSegmentCountForRoute(route);
    int maxCount = (int) (segmentCount * downsampleRatio);

    while (stopTags.size() < maxCount) {
      Counter<String> counter = new Counter<String>();
      for (Direction direction : route.getDirections()) {
        List<Stop> stops = direction.getStops();
        int[] minDistance = getMinDistanceToActiveStop(stops, stopTags);
        for (int i = 0; i < stops.size(); i++) {
          Stop stop = stops.get(i);
          String tag = stop.getTag();
          if (!stopTags.contains(tag)) {
            counter.increment(tag, minDistance[i]);
          }
        }
      }
      String max = counter.getMax();
      stopTags.add(max);
    }
    return new RouteStopCoverage(route.getTag(), stopTags);
  }

  /**
   * As described in {@link #getSegmentCountForRoute(Route)}, this method counts
   * the number of unique from_stop-to_stop pairs in each route configuration
   * and sums them all together.
   * 
   * @param routeConfigurations
   * @return
   */
  private int getSegmentCountForRoutes(List<Route> routeConfigurations) {
    int segmentCount = 0;
    for (Route route : routeConfigurations) {
      segmentCount += getSegmentCountForRoute(route);
    }
    return segmentCount;
  }

  /**
   * A segment is a from_stop-to_stop pair contained in the stop sequences of a
   * particular {@link Route} object. Here, we count the number of unique
   * segment pairs in a route to give us a measure of the relative complexity of
   * the route.
   * 
   * @param route
   * @return
   */
  private int getSegmentCountForRoute(Route route) {
    Set<String> segments = new HashSet<String>();
    for (Direction direction : route.getDirections()) {
      List<Stop> stops = direction.getStops();
      for (int i = 0; i + 1 < stops.size(); i++) {
        Stop prev = stops.get(i);
        Stop next = stops.get(i + 1);
        segments.add(prev.getTag() + "|" + next.getTag());
      }
    }
    int count = segments.size();
    return count;
  }

  private int[] getMinDistanceToActiveStop(List<Stop> stops,
      Set<String> activeStops) {
    int[] left = getMinDistanceOnLeftToActiveStop(stops, activeStops);
    int[] right = getMinDistanceOnRightToActiveStop(stops, activeStops);
    int[] min = new int[stops.size()];
    for (int i = 0; i < min.length; i++) {
      min[i] = Math.min(left[i], right[i]);
    }
    return min;
  }

  private int[] getMinDistanceOnLeftToActiveStop(List<Stop> stops,
      Set<String> activeStops) {
    int[] minDistanceOnLeft = new int[stops.size()];
    int currentMin = 0;
    for (int i = 0; i < stops.size(); i++) {
      Stop stop = stops.get(i);
      if (activeStops.contains(stop.getTag())) {
        currentMin = 0;
      }
      minDistanceOnLeft[i] = currentMin;
      currentMin++;
    }
    return minDistanceOnLeft;
  }

  private int[] getMinDistanceOnRightToActiveStop(List<Stop> stops,
      Set<String> includedStops) {
    int[] minDistanceOnRight = new int[stops.size()];
    int currentMin = 0;
    for (int i = stops.size() - 1; i >= 0; i--) {
      Stop stop = stops.get(i);
      if (includedStops.contains(stop.getTag())) {
        currentMin = 0;
      }
      minDistanceOnRight[i] = currentMin;
      currentMin++;
    }
    return minDistanceOnRight;
  }

  private class RefreshTask implements Runnable {

    @Override
    public void run() {
      try {
        refreshRouteStopCoverage(false);
        scheduleNextRefresh();
      } catch (Exception ex) {
        _log.info("error refreshing route stop coverage", ex);
      }
    }

  }
}
