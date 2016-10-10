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

import java.io.IOException;
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

import org.onebusaway.collections.Counter;
import org.onebusaway.gtfs_realtime.nextbus.model.RouteStopCoverage;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBDirection;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBRoute;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBStop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private NextBusApiService _nextBusApiService;

  private NextBusToGtfsService _matchingService;

  private ScheduledExecutorService _executor;

  private volatile List<RouteStopCoverage> _routeStopCoverage = Collections.emptyList();

  private final RefreshTask _refreshTask = new RefreshTask();

  private ScheduledFuture<?> _refreshTaskInstance;

  @Inject
  public void setNextBusApiService(NextBusApiService nextBusApiService) {
    _nextBusApiService = nextBusApiService;
  }

  @Inject
  public void setMatchingService(NextBusToGtfsService matchingService) {
    _matchingService = matchingService;
  }

  @Inject
  public void setExecutor(ScheduledExecutorService executor) {
    _executor = executor;
  }

  public synchronized List<RouteStopCoverage> getRouteStopCoverage() {
    while (_routeStopCoverage.isEmpty()) {
      try {
        wait(1000);
      } catch (InterruptedException e) {
        return _routeStopCoverage;
      }
    }
    return _routeStopCoverage;
  }

  public String mapStopTagForRoute(String stopTag, String stopTag2) {

    return null;
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
      throws IOException, ClassNotFoundException {
    _log.info("Rebuilding route-stop coverage model");
    List<NBRoute> routeConfigurations = readRouteConfigurations(useCacheIfAvailable);
    _routeStopCoverage = getRouteStopCoverageForRouteConfigurations(routeConfigurations);
    _matchingService.matchToGtfs(routeConfigurations);
    notifyAll();
  }

  private List<NBRoute> readRouteConfigurations(boolean useCacheIfAvailable)
      throws IOException, ClassNotFoundException {
    List<NBRoute> routeConfigurations = downloadRouteConfigurations();
    return routeConfigurations;
  }

  private List<NBRoute> downloadRouteConfigurations() throws IOException {
    List<NBRoute> routes = _nextBusApiService.downloadRouteList();
    List<NBRoute> routeConfigurations = new ArrayList<NBRoute>();
    int routeIndex = 0;
    for (NBRoute route : routes) {
      _log.info("routes processed=" + (++routeIndex) + "/" + routes.size());
      List<NBRoute> routeConfigs = _nextBusApiService.downloadRouteConfigList(route.getTag());
      routeConfigurations.addAll(routeConfigs);
    }
    return routeConfigurations;
  }

  private List<RouteStopCoverage> getRouteStopCoverageForRouteConfigurations(
      List<NBRoute> routeConfigurations) {
    double downsampleRatio = computeDownsampleRatioForRouteConfigurations(routeConfigurations);
    List<RouteStopCoverage> coverage = new ArrayList<RouteStopCoverage>();
    for (NBRoute route : routeConfigurations) {
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
      List<NBRoute> routeConfigurations) {
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

  private RouteStopCoverage computeRouteStopCoverageForRoute(NBRoute route,
      double downsampleRatio) {
    Set<String> stopTags = new HashSet<String>();
    /**
     * We add the trip ends no matter what
     */
    for (NBDirection direction : route.getDirections()) {
      List<NBStop> stops = direction.getStops();
      NBStop lastStop = stops.get(stops.size() - 1);
      stopTags.add(lastStop.getTag());
    }
    int segmentCount = getSegmentCountForRoute(route);
    int maxCount = (int) (segmentCount * downsampleRatio);

    while (stopTags.size() < maxCount) {
      Counter<String> counter = new Counter<String>();
      for (NBDirection direction : route.getDirections()) {
        List<NBStop> stops = direction.getStops();
        int[] minDistance = getMinDistanceToActiveStop(stops, stopTags);
        for (int i = 0; i < stops.size(); i++) {
          NBStop stop = stops.get(i);
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
   * As described in {@link #getSegmentCountForRoute(NBRoute)}, this method
   * counts the number of unique from_stop-to_stop pairs in each route
   * configuration and sums them all together.
   * 
   * @param routeConfigurations
   * @return
   */
  private int getSegmentCountForRoutes(List<NBRoute> routeConfigurations) {
    int segmentCount = 0;
    for (NBRoute route : routeConfigurations) {
      segmentCount += getSegmentCountForRoute(route);
    }
    return segmentCount;
  }

  /**
   * A segment is a from_stop-to_stop pair contained in the stop sequences of a
   * particular {@link NBRoute} object. Here, we count the number of unique
   * segment pairs in a route to give us a measure of the relative complexity of
   * the route.
   * 
   * @param route
   * @return
   */
  private int getSegmentCountForRoute(NBRoute route) {
    Set<String> segments = new HashSet<String>();
    for (NBDirection direction : route.getDirections()) {
      List<NBStop> stops = direction.getStops();
      for (int i = 0; i + 1 < stops.size(); i++) {
        NBStop prev = stops.get(i);
        NBStop next = stops.get(i + 1);
        segments.add(prev.getTag() + "|" + next.getTag());
      }
    }
    int count = segments.size();
    return count;
  }

  private int[] getMinDistanceToActiveStop(List<NBStop> stops,
      Set<String> activeStops) {
    int[] left = getMinDistanceOnLeftToActiveStop(stops, activeStops);
    int[] right = getMinDistanceOnRightToActiveStop(stops, activeStops);
    int[] min = new int[stops.size()];
    for (int i = 0; i < min.length; i++) {
      min[i] = Math.min(left[i], right[i]);
    }
    return min;
  }

  private int[] getMinDistanceOnLeftToActiveStop(List<NBStop> stops,
      Set<String> activeStops) {
    int[] minDistanceOnLeft = new int[stops.size()];
    int currentMin = 0;
    for (int i = 0; i < stops.size(); i++) {
      NBStop stop = stops.get(i);
      if (activeStops.contains(stop.getTag())) {
        currentMin = 0;
      }
      minDistanceOnLeft[i] = currentMin;
      currentMin++;
    }
    return minDistanceOnLeft;
  }

  private int[] getMinDistanceOnRightToActiveStop(List<NBStop> stops,
      Set<String> includedStops) {
    int[] minDistanceOnRight = new int[stops.size()];
    int currentMin = 0;
    for (int i = stops.size() - 1; i >= 0; i--) {
      NBStop stop = stops.get(i);
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
