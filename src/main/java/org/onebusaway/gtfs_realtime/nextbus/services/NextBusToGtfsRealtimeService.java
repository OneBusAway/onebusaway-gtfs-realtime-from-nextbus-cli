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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.collections.MappingLibrary;
import org.onebusaway.gtfs_realtime.nextbus.model.FlatPrediction;
import org.onebusaway.gtfs_realtime.nextbus.model.RouteStopCoverage;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBDirection;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBPrediction;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBPredictions;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeProvider;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;

@Singleton
public class NextBusToGtfsRealtimeService implements GtfsRealtimeProvider {

  private static final Logger _log = LoggerFactory.getLogger(NextBusToGtfsRealtimeService.class);

  private static final PredictionComparator _predictionComparator = new PredictionComparator();

  private RouteStopCoverageService _routeStopCoverageService;

  private NextBusApiService _nextBusApiService;

  private NextBusToGtfsService _nextBusToGtfsService;

  private ConcurrentMap<String, TripUpdateWithTimestamp> _tripUpdatesByTripId = new ConcurrentHashMap<String, TripUpdateWithTimestamp>();

  private volatile FeedMessage _tripUpdatesMessage = GtfsRealtimeLibrary.createFeedMessageBuilder().build();

  private final FeedMessage _vehiclePositionsMessage = GtfsRealtimeLibrary.createFeedMessageBuilder().build();

  private final FeedMessage _alertsMessage = GtfsRealtimeLibrary.createFeedMessageBuilder().build();

  private ExecutorService _executor;

  private Future<?> _task;

  /**
   * The minimum amount of time, in seconds, between repeated requests for the
   * same route.
   */
  private int _minimumTimeBetweenRequests = 30;

  @Inject
  public void setRouteStopCoverageService(
      RouteStopCoverageService routeStopCoverageService) {
    _routeStopCoverageService = routeStopCoverageService;
  }

  @Inject
  public void setNextBusApiService(NextBusApiService nextBusApiService) {
    _nextBusApiService = nextBusApiService;
  }

  @Inject
  public void setNextBusToGtfsService(NextBusToGtfsService nextBusToGtfsService) {
    _nextBusToGtfsService = nextBusToGtfsService;
  }

  /**
   * Sets the minimum amount of time, in seconds, between repeated requests for
   * the same route.
   * 
   * @param mininmumTimeInSeconds
   */
  public void setMinimumTimeBetweenRequests(int mininmumTimeInSeconds) {
    _minimumTimeBetweenRequests = mininmumTimeInSeconds;
  }

  /****
   * {@link GtfsRealtimeProvider} Interface
   ****/

  @Override
  public FeedMessage getTripUpdates() {
    return _tripUpdatesMessage;
  }

  @Override
  public FeedMessage getVehiclePositions() {
    return _vehiclePositionsMessage;
  }

  @Override
  public FeedMessage getAlerts() {
    return _alertsMessage;
  }

  /****
   * Service Entry Point
   ****/

  @PostConstruct
  public void start() {
    _executor = Executors.newSingleThreadExecutor();
    _task = _executor.submit(new ProcessingTask());
  }

  @PreDestroy
  public void stop() {
    if (_task != null) {
      _task.cancel(true);
      _task = null;
    }
    if (_executor != null) {
      _executor.shutdownNow();
      _executor = null;
    }
  }

  private void processRouteStopCoverage(RouteStopCoverage routeStopCoverage)
      throws IOException, SAXException {

    _log.info("route=" + routeStopCoverage.getRouteTag());

    List<NBPredictions> allPredictions = _nextBusApiService.downloadPredictions(routeStopCoverage);
    List<FlatPrediction> flatPredictions = flattenPredictions(allPredictions);
    List<FlatPrediction> predictionsWithTripIds = getPredictionsWithTripIds(flatPredictions);

    Map<String, List<FlatPrediction>> predictionsByTripId = MappingLibrary.mapToValueList(
        predictionsWithTripIds, "tripTag");
    processPredictionGroup(predictionsByTripId);
  }

  private List<FlatPrediction> flattenPredictions(
      List<NBPredictions> allPredictions) {

    List<FlatPrediction> flattened = new ArrayList<FlatPrediction>();
    for (NBPredictions predictions : allPredictions) {
      for (NBDirection direction : predictions.getDirections()) {
        for (NBPrediction prediction : direction.getPredictions()) {
          FlatPrediction flat = new FlatPrediction();
          flat.setBlock(prediction.getBlock());
          flat.setDirTag(prediction.getDirTag());
          flat.setEpochTime(prediction.getEpochTime());
          flat.setRouteTag(predictions.getRouteTag());
          flat.setStopTag(predictions.getStopTag());
          flat.setTripTag(prediction.getTripTag());
          flat.setVehicle(prediction.getVehicle());
          flattened.add(flat);
        }
      }
    }

    _nextBusToGtfsService.mapToGtfsIfApplicable(flattened);

    return flattened;
  }

  private List<FlatPrediction> getPredictionsWithTripIds(
      List<FlatPrediction> predictions) {
    List<FlatPrediction> predictionsWithTripId = new ArrayList<FlatPrediction>();
    for (FlatPrediction prediction : predictions) {
      if (prediction.getTripTag() != null)
        predictionsWithTripId.add(prediction);
    }
    return predictionsWithTripId;
  }

  private void processPredictionGroup(
      Map<String, List<FlatPrediction>> predictionsById) {
    for (Map.Entry<String, List<FlatPrediction>> entry : predictionsById.entrySet()) {
      String tripId = entry.getKey();
      List<FlatPrediction> predictions = entry.getValue();
      Collections.sort(predictions, _predictionComparator);
      FlatPrediction first = predictions.get(0);

      TripUpdate.Builder tripUpdate = TripUpdate.newBuilder();
      TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder();
      tripDescriptor.setTripId(tripId);
      tripUpdate.setTrip(tripDescriptor);
      VehicleDescriptor.Builder vehicle = VehicleDescriptor.newBuilder();
      vehicle.setId(first.getVehicle());
      tripUpdate.setVehicle(vehicle);

      for (FlatPrediction prediction : predictions) {
        StopTimeUpdate.Builder stopTimeUpdate = StopTimeUpdate.newBuilder();
        stopTimeUpdate.setStopId(prediction.getStopTag());
        StopTimeEvent.Builder stopTimeEvent = StopTimeEvent.newBuilder();
        stopTimeEvent.setTime(prediction.getEpochTime() / 1000);
        stopTimeUpdate.setDeparture(stopTimeEvent);
        tripUpdate.addStopTimeUpdate(stopTimeUpdate);
      }
      TripUpdateWithTimestamp withTimestamp = new TripUpdateWithTimestamp(
          tripUpdate.build());
      _tripUpdatesByTripId.put(tripId, withTimestamp);
    }
  }

  private void writeGtfsRealtimeOutput() {
    Iterator<TripUpdateWithTimestamp> iterator = _tripUpdatesByTripId.values().iterator();

    FeedMessage.Builder feedMessageBuilder = GtfsRealtimeLibrary.createFeedMessageBuilder();
    long now = feedMessageBuilder.getHeader().getTimestamp();

    while (iterator.hasNext()) {
      TripUpdateWithTimestamp update = iterator.next();
      if (update.getTimestamp() / 1000 + 5 * 60 < now) {
        iterator.remove();
      } else {
        TripUpdate tripUpdate = update.getTripUpdate();
        FeedEntity.Builder entity = FeedEntity.newBuilder();
        entity.setId(tripUpdate.getTrip().getTripId());
        entity.setTripUpdate(tripUpdate);
        feedMessageBuilder.addEntity(entity);
      }
    }

    _tripUpdatesMessage = feedMessageBuilder.build();
  }

  private class ProcessingTask implements Runnable {

    @Override
    public void run() {

      while (true) {
        List<RouteStopCoverage> coverage = _routeStopCoverageService.getRouteStopCoverage();
        long t0 = System.currentTimeMillis();
        for (RouteStopCoverage routeStopCoverage : coverage) {
          if (Thread.interrupted()) {
            return;
          }
          try {
            processRouteStopCoverage(routeStopCoverage);
            writeGtfsRealtimeOutput();
          } catch (Exception ex) {

          }
        }
        long t1 = System.currentTimeMillis();

        /**
         * Check to see if we need to wait a while before making our next batch
         * of route requests.
         */
        long remainingTime = _minimumTimeBetweenRequests * 1000 - (t1 - t0);
        if (remainingTime > 0) {
          try {
            _log.info("sleeping for " + remainingTime);
            Thread.sleep(remainingTime);
          } catch (InterruptedException e) {
            return;
          }
        }
      }

    }
  }

  private static class TripUpdateWithTimestamp {
    private final long timestamp = System.currentTimeMillis();
    private final TripUpdate tripUpdate;

    public TripUpdateWithTimestamp(TripUpdate tripUpdate) {
      this.tripUpdate = tripUpdate;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public TripUpdate getTripUpdate() {
      return tripUpdate;
    }
  }

  private static class PredictionComparator implements
      Comparator<FlatPrediction> {
    @Override
    public int compare(FlatPrediction o1, FlatPrediction o2) {
      long t1 = o1.getEpochTime();
      long t2 = o2.getEpochTime();
      return t1 == t2 ? 0 : (t1 < t2 ? -1 : 1);
    }
  }
}
