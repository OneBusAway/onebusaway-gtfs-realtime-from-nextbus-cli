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
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.Alerts;
import org.onebusaway.gtfs_realtime.nextbus.model.FlatPrediction;
import org.onebusaway.gtfs_realtime.nextbus.model.RouteStopCoverage;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBDirection;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBMessage;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBPrediction;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBPredictions;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBRoute;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBStop;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBVehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.EntitySelector;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TimeRange;
import com.google.transit.realtime.GtfsRealtime.TranslatedString;
import com.google.transit.realtime.GtfsRealtime.TranslatedString.Translation;
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
  
  private NextBusToGtfsService _matchingService;

  private NextBusApiService _nextBusApiService;

  private NextBusToGtfsService _nextBusToGtfsService;

  private GtfsRealtimeSink _tripUpdatesSink;

  private GtfsRealtimeSink _vehiclePositionsSink;
  
  private GtfsRealtimeSink _alertsSink;

  private ExecutorService _executor;

  private Future<?> _task;

  private Map<String, Long> _prevVehiclePositionRequestTimeByRouteTag = new HashMap<String, Long>();
  
  private Long _prevAlertRequest = 0L;

  /**
   * The minimum amount of time, in seconds, between repeated requests for the
   * same route.
   */
  private int _minimumTimeBetweenRequests = 30;

  private boolean _tripUpdatesEnabled = false;

  private boolean _vehiclePositionsEnabled = false;
  
  private boolean _alertsEnabled = false;

  @Inject
  public void setRouteStopCoverageService(
      RouteStopCoverageService routeStopCoverageService) {
    _routeStopCoverageService = routeStopCoverageService;
  }
  
  @Inject
  public void setMatchingService(NextBusToGtfsService matchingService) {
    _matchingService = matchingService;
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
  
  @Inject
  public void setAlertsSink(@Alerts
  GtfsRealtimeSink alertsSink) {
    _alertsSink = alertsSink;
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
  
  public void setEnableAlerts(boolean enableAlerts) {
    _alertsEnabled = enableAlerts;
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
      throws IOException, SAXException {

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
      throws IOException, SAXException {
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

  private void generateVehiclePositions(String routeTag) throws IOException,
      SAXException {
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
  
  private void processMessages()
      throws IOException, SAXException {
	if (_alertsEnabled) {
	  generateAlerts();
	}
  }

  private void generateAlerts() throws IOException, SAXException {
    List<NBRoute> allRoutes = _nextBusApiService.downloadMessages(_prevAlertRequest);
    _prevAlertRequest = System.currentTimeMillis();

    GtfsRealtimeIncrementalUpdate update = new GtfsRealtimeIncrementalUpdate();
    ArrayList<String> alertIds = new ArrayList<String>();
    for (NBRoute route: allRoutes) {
      for (NBMessage message: route.getMessages()) {
        if(!alertIds.contains(message.getId())) {
          Alert.Builder alert = Alert.newBuilder();
          
          TranslatedString.Builder translatedString = TranslatedString.newBuilder();
          Translation.Builder translation = Translation.newBuilder();
          translation.setLanguage("en");
          translation.setText(message.getText());
          translatedString.addTranslation(translation);
          alert.setHeaderText(translatedString);
          
          // add timerange if NextBus message has boundary(ies)
          if(message.getStartBoundary() > 0 || message.getEndBoundary() > 0) {
        	TimeRange.Builder timeRange = TimeRange.newBuilder();
        	if(message.getStartBoundary() > 0) {
        	  timeRange.setStart(message.getStartBoundary() / 1000);
        	}
        	if(message.getEndBoundary() > 0) {
        	  timeRange.setEnd(message.getEndBoundary() / 1000);
        	}
        	alert.addActivePeriod(timeRange);
          }
          
          // add informed entity(ies) if NextBus message has affected routes (and stops)
          if(message.getRoutes().size() > 0) {
        	List<NBRoute> messageRoutes = message.getRoutes();
            _matchingService.matchToGtfs(messageRoutes);
        	
        	for(NBRoute messageRoute: messageRoutes){
        	  EntitySelector.Builder routeEntity = EntitySelector.newBuilder();
        	  routeEntity.setRouteId(messageRoute.getTag());
        	  alert.addInformedEntity(routeEntity);
        	  for(NBStop routeStop: messageRoute.getStops()) {
        		EntitySelector.Builder routeStopEntity = EntitySelector.newBuilder();
        		routeStopEntity.setRouteId(messageRoute.getTag());
        		if(routeStop.getStopId() != null) {
        		  routeStopEntity.setStopId(routeStop.getStopId());
        		} else {
        		  routeStopEntity.setStopId(routeStop.getTag());
        		}
            	alert.addInformedEntity(routeStopEntity);
        	  }
        	}
          }
          
          FeedEntity.Builder feedEntity = FeedEntity.newBuilder();
          feedEntity.setAlert(alert);
          feedEntity.setId(message.getId());
          update.addUpdatedEntity(feedEntity.build());
          
          alertIds.add(message.getId());
        }
      }
    }
    _alertsSink.handleIncrementalUpdate(update);
  }

  private class ProcessingTask implements Runnable {

    @Override
    public void run() {

      while (true) {
    	long t0 = System.currentTimeMillis();
    	
    	if(_tripUpdatesEnabled || _vehiclePositionsEnabled) {
    	  
    	  List<RouteStopCoverage> coverage = _routeStopCoverageService.getRouteStopCoverage();
        
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
        }
        
        try {
          processMessages();
        } catch (Exception ex) {
          _log.warn("error processing messages: ", ex);
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
