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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.collections.FactoryMap;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeIncrementalUpdate;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeSink;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.TripUpdates;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.VehiclePositions;
import org.onebusaway.gtfs_realtime.nextbus.model.FlatPrediction;
import org.onebusaway.gtfs_realtime.nextbus.model.RouteStopCoverage;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBDirection;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBPrediction;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBPredictions;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBVehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

@Singleton
public class NextBusToGtfsRealtimeService {

  private static final Logger _log = LoggerFactory.getLogger(NextBusToGtfsRealtimeService.class);

  private static final PredictionComparator _predictionComparator = new PredictionComparator();

  private RouteStopCoverageService _routeStopCoverageService;

  private NextBusApiService _nextBusApiService;

  private NextBusToGtfsService _nextBusToGtfsService;

  private GtfsRealtimeSink _tripUpdatesSink;

  private GtfsRealtimeSink _vehiclePositionsSink;

  private ExecutorService _executor;

  private Future<?> _task;

  private Map<String, Long> _prevVehiclePositionRequestTimeByRouteTag = new HashMap<String, Long>();

  /**
   * The minimum amount of time, in seconds, between repeated requests for the
   * same route.
   */
  private int _minimumTimeBetweenRequests = 30;

  private boolean _tripUpdatesEnabled = false;

  private boolean _vehiclePositionsEnabled = false;

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

  @Inject
  public void setTripUpdatesSink(@TripUpdates
  GtfsRealtimeSink tripUpdatesSink) {
    _tripUpdatesSink = tripUpdatesSink;
  }

  @Inject
  public void setVehiclePositionsSink(@VehiclePositions
  GtfsRealtimeSink vehiclePositionsSink) {
    _vehiclePositionsSink = vehiclePositionsSink;
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

  public void setEnableTripUpdates(boolean enableTripUpdates) {
    _tripUpdatesEnabled = enableTripUpdates;
  }

  public void setEnableVehiclePositions(boolean enableVehiclePositions) {
    _vehiclePositionsEnabled = enableVehiclePositions;
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

  private void processRoute(RouteStopCoverage routeStopCoverage)
      throws IOException {

    String routeTag = routeStopCoverage.getRouteTag();
    _log.info("route=" + routeTag);

    if (_tripUpdatesEnabled) {
      generateTripUpdates(routeStopCoverage);
    }

    if (_vehiclePositionsEnabled) {
      generateVehiclePositions(routeTag);
    }
  }

  private void generateTripUpdates(RouteStopCoverage routeStopCoverage)
      throws IOException {
    List<NBPredictions> allPredictions = _nextBusApiService.downloadPredictions(routeStopCoverage);
    List<FlatPrediction> flatPredictions = flattenPredictions(allPredictions);
    Map<TripUpdateId, List<FlatPrediction>> predictionsById = groupPredictionsById(flatPredictions);
    processPredictionGroup(predictionsById);
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

  private Map<TripUpdateId, List<FlatPrediction>> groupPredictionsById(
      List<FlatPrediction> flatPredictions) {
    Map<TripUpdateId, List<FlatPrediction>> predictionsById = new FactoryMap<TripUpdateId, List<FlatPrediction>>(
        new ArrayList<FlatPrediction>());
    for (FlatPrediction prediction : flatPredictions) {
      String vehicleId = prediction.getVehicle();
      if (vehicleId == null) {
        continue;
      }
      String tripId = prediction.getTripTag();
      TripUpdateId id = new TripUpdateId(vehicleId, tripId);
      predictionsById.get(id).add(prediction);
    }
    return predictionsById;
  }

  private void processPredictionGroup(
      Map<TripUpdateId, List<FlatPrediction>> predictionsById) {
    GtfsRealtimeIncrementalUpdate update = new GtfsRealtimeIncrementalUpdate();
    for (Map.Entry<TripUpdateId, List<FlatPrediction>> entry : predictionsById.entrySet()) {
      TripUpdateId id = entry.getKey();
      List<FlatPrediction> predictions = entry.getValue();
      Collections.sort(predictions, _predictionComparator);
      FlatPrediction first = predictions.get(0);

      TripUpdate.Builder tripUpdate = TripUpdate.newBuilder();
      TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder();
      if (first.getRouteTag() != null) {
        tripDescriptor.setRouteId(first.getRouteTag());
      }
      if (id.getTripId() != null) {
        tripDescriptor.setTripId(id.getTripId());
      }
      tripUpdate.setTrip(tripDescriptor);
      VehicleDescriptor.Builder vehicle = VehicleDescriptor.newBuilder();
      vehicle.setId(id.getVehicleId());
      tripUpdate.setVehicle(vehicle);

      for (FlatPrediction prediction : predictions) {
        StopTimeUpdate.Builder stopTimeUpdate = StopTimeUpdate.newBuilder();
        stopTimeUpdate.setStopId(prediction.getStopTag());
        StopTimeEvent.Builder stopTimeEvent = StopTimeEvent.newBuilder();
        stopTimeEvent.setTime(prediction.getEpochTime() / 1000);
        stopTimeUpdate.setDeparture(stopTimeEvent);
        tripUpdate.addStopTimeUpdate(stopTimeUpdate);
      }
      FeedEntity.Builder feedEntity = FeedEntity.newBuilder();
      feedEntity.setId(id.getFeedEntityId());
      feedEntity.setTripUpdate(tripUpdate);
      update.addUpdatedEntity(feedEntity.build());
    }
    _tripUpdatesSink.handleIncrementalUpdate(update);
  }

  private void generateVehiclePositions(String routeTag) throws IOException {
    long prevRequestTimeOrZero = 0;
    Long prevRequestTime = _prevVehiclePositionRequestTimeByRouteTag.get(routeTag);
    if (prevRequestTime != null) {
      prevRequestTimeOrZero = prevRequestTime;
    }
    long currentRequestTime = System.currentTimeMillis();
    List<NBVehicle> vehicles = _nextBusApiService.downloadVehicleLocations(
        routeTag, prevRequestTimeOrZero);
    _prevVehiclePositionRequestTimeByRouteTag.put(routeTag, currentRequestTime);

    GtfsRealtimeIncrementalUpdate update = new GtfsRealtimeIncrementalUpdate();
    for (NBVehicle vehicle : vehicles) {
      VehiclePosition.Builder vehiclePosition = VehiclePosition.newBuilder();

      Position.Builder position = vehiclePosition.getPositionBuilder();
      position.setLatitude((float) vehicle.getLat());
      position.setLongitude((float) vehicle.getLon());
      position.setBearing(vehicle.getHeading());

      VehicleDescriptor.Builder vehicleDesc = vehiclePosition.getVehicleBuilder();
      vehicleDesc.setId(vehicle.getId());

      FeedEntity.Builder feedEntity = FeedEntity.newBuilder();
      feedEntity.setVehicle(vehiclePosition);
      feedEntity.setId(vehicle.getId());
      update.addUpdatedEntity(feedEntity.build());
    }
    _vehiclePositionsSink.handleIncrementalUpdate(update);
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
            processRoute(routeStopCoverage);
          } catch (Exception ex) {
            _log.warn("error processing routeStopCoverage: ", ex);
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
