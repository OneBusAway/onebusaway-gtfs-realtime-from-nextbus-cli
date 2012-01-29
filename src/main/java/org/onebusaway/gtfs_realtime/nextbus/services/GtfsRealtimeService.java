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
import java.io.InputStream;
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

import org.apache.commons.digester.Digester;
import org.onebusaway.collections.FactoryMap;
import org.onebusaway.gtfs_realtime.nextbus.model.RouteStopCoverage;
import org.onebusaway.gtfs_realtime.nextbus.model.api.Direction;
import org.onebusaway.gtfs_realtime.nextbus.model.api.Prediction;
import org.onebusaway.gtfs_realtime.nextbus.model.api.Predictions;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeProvider;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeSupport;
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
public class GtfsRealtimeService implements GtfsRealtimeProvider {

  private static final Logger _log = LoggerFactory.getLogger(GtfsRealtimeService.class);

  private static final PredictionComparator _predictionComparator = new PredictionComparator();

  private DownloaderService _downloader;

  private RouteStopCoverageService _routeStopCoverageService;

  private String _agencyId;

  private ConcurrentMap<String, TripUpdateWithTimestamp> _tripUpdatesByTripId = new ConcurrentHashMap<String, TripUpdateWithTimestamp>();

  private volatile FeedMessage _tripUpdatesMessage = GtfsRealtimeSupport.createFeedMessageBuilder().build();

  private final FeedMessage _vehiclePositionsMessage = GtfsRealtimeSupport.createFeedMessageBuilder().build();

  private final FeedMessage _alertsMessage = GtfsRealtimeSupport.createFeedMessageBuilder().build();

  private ExecutorService _executor;

  private Future<?> _task;

  /**
   * The minimum amount of time, in seconds, between repeated requests for the
   * same route.
   */
  private int _minimumTimeBetweenRequests = 30;

  @Inject
  public void setDownloader(DownloaderService downloader) {
    _downloader = downloader;
  }

  @Inject
  public void setRouteStopCoverageService(
      RouteStopCoverageService routeStopCoverageService) {
    _routeStopCoverageService = routeStopCoverageService;
  }

  public void setAgencyId(String agencyId) {
    _agencyId = agencyId;
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

  private void processRouteStopCoverage(Digester digester,
      RouteStopCoverage routeStopCoverage) throws IOException, SAXException {

    _log.info("route=" + routeStopCoverage.getRouteTag());

    Map<String, List<Prediction>> predictionsByTripId = new FactoryMap<String, List<Prediction>>(
        new ArrayList<Prediction>());

    List<Predictions> allPredictions = downloadPredictions(digester,
        routeStopCoverage);

    for (Predictions predictions : allPredictions) {
      for (Direction direction : predictions.getDirections()) {
        for (Prediction prediction : direction.getPredictions()) {
          prediction.setStopTag(predictions.getStopTag());
          String tripId = prediction.getTripTag();
          predictionsByTripId.get(tripId).add(prediction);
        }
      }
    }

    for (Map.Entry<String, List<Prediction>> entry : predictionsByTripId.entrySet()) {
      String tripId = entry.getKey();
      List<Prediction> predictions = entry.getValue();
      Collections.sort(predictions, _predictionComparator);
      Prediction first = predictions.get(0);

      TripUpdate.Builder tripUpdate = TripUpdate.newBuilder();
      TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder();
      tripDescriptor.setTripId(tripId);
      tripUpdate.setTrip(tripDescriptor);
      VehicleDescriptor.Builder vehicle = VehicleDescriptor.newBuilder();
      vehicle.setId(first.getVehicle());
      tripUpdate.setVehicle(vehicle);

      for (Prediction prediction : predictions) {
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

  private Digester getDigester() {
    Digester digester = new Digester();
    digester.addObjectCreate("body", ArrayList.class);

    digester.addObjectCreate("body/predictions", Predictions.class);
    digester.addSetProperties("body/predictions");
    digester.addSetNext("body/predictions", "add");

    digester.addObjectCreate("body/predictions/direction", Direction.class);
    digester.addSetProperties("body/predictions/direction");
    digester.addSetNext("body/predictions/direction", "addDirection");

    digester.addObjectCreate("body/predictions/direction/prediction",
        Prediction.class);
    digester.addSetProperties("body/predictions/direction/prediction");
    digester.addSetNext("body/predictions/direction/prediction",
        "addPrediction");

    return digester;
  }

  @SuppressWarnings("unchecked")
  private List<Predictions> downloadPredictions(Digester digester,
      RouteStopCoverage coverage) throws IOException, SAXException {
    String url = "http://webservices.nextbus.com/service/publicXMLFeed?command=predictionsForMultiStops&a="
        + _agencyId;
    for (String stopTag : coverage.getStopTags()) {
      url += "&stops=" + coverage.getRouteTag() + "%7c" + stopTag;
    }
    InputStream in = _downloader.openUrl(url);
    return (List<Predictions>) digester.parse(in);
  }

  private void writeGtfsRealtimeOutput() {
    Iterator<TripUpdateWithTimestamp> iterator = _tripUpdatesByTripId.values().iterator();

    FeedMessage.Builder feedMessageBuilder = GtfsRealtimeSupport.createFeedMessageBuilder();
    long now = feedMessageBuilder.getHeader().getTimestamp();

    while (iterator.hasNext()) {
      TripUpdateWithTimestamp update = iterator.next();
      if (update.getTimestamp() + 5 * 60 * 1000 < now) {
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
      Digester digester = getDigester();

      while (true) {
        List<RouteStopCoverage> coverage = _routeStopCoverageService.getRouteStopCoverage();
        long t0 = System.currentTimeMillis();
        for (RouteStopCoverage routeStopCoverage : coverage) {
          if (Thread.interrupted()) {
            return;
          }
          try {
            processRouteStopCoverage(digester, routeStopCoverage);
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

  private static class PredictionComparator implements Comparator<Prediction> {
    @Override
    public int compare(Prediction o1, Prediction o2) {
      long t1 = o1.getEpochTime();
      long t2 = o2.getEpochTime();
      return t1 == t2 ? 0 : (t1 < t2 ? -1 : 1);
    }
  }

}
