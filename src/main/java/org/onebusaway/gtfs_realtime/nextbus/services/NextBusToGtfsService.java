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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.collections.MappingLibrary;
import org.onebusaway.collections.Min;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.gtfs_realtime.nextbus.model.FlatPrediction;
import org.onebusaway.gtfs_realtime.nextbus.model.RouteDirectionStopKey;
import org.onebusaway.gtfs_realtime.nextbus.model.ServiceDateBlockKey;
import org.onebusaway.gtfs_realtime.nextbus.model.StopTimeIndex;
import org.onebusaway.gtfs_realtime.nextbus.model.StopTimeIndices;
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

  private ConcurrentMap<ServiceDateBlockKey, StopTimeIndices> _stopTimeMappings = new ConcurrentHashMap<ServiceDateBlockKey, StopTimeIndices>();

  private TimeZone _agencyTimeZone = TimeZone.getDefault();

  private Map<String, VehicleStatus> _vehicleStatusById = new HashMap<String, NextBusToGtfsService.VehicleStatus>();

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

    updateTimeZone(dao);

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
      Map<ServiceDateBlockKey, StopTimeIndices> mapping = _tripMatching.getTripMatches(
          routeMatches, stopIdMappings, dao);
      _stopTimeMappings.clear();
      _stopTimeMappings.putAll(mapping);
    }
  }

  public void mapToGtfsIfApplicable(List<FlatPrediction> predictions) {
    if (_gtfsPath == null)
      return;

    for (FlatPrediction prediction : predictions) {
      String updatedRouteTag = _routeIdMappings.get(prediction.getRouteTag());
      String updatedStopTag = _stopIdMappings.get(new RouteDirectionStopKey(
          prediction.getRouteTag(), prediction.getDirTag(),
          prediction.getStopTag()));

      if (updatedRouteTag != null)
        prediction.setRouteTag(updatedRouteTag);
      if (updatedStopTag != null)
        prediction.setStopTag(updatedStopTag);
    }

    if (_gtfsTripMatching) {
      Map<String, List<FlatPrediction>> predictionsByVehicleId = MappingLibrary.mapToValueList(
          predictions, "vehicle");
      for (Map.Entry<String, List<FlatPrediction>> tripEntry : predictionsByVehicleId.entrySet()) {
        String vehicleId = tripEntry.getKey();
        VehicleStatus status = updateVehicleStatus(vehicleId);
        List<FlatPrediction> predictionsForVehicle = tripEntry.getValue();
        Map<String, List<FlatPrediction>> predictionsByBlock = MappingLibrary.mapToValueList(
            predictionsForVehicle, "block");
        for (Map.Entry<String, List<FlatPrediction>> blockEntry : predictionsByBlock.entrySet()) {
          String blockId = blockEntry.getKey();
          List<FlatPrediction> predictionsForBlock = blockEntry.getValue();
          Collections.sort(predictionsForBlock);
          FlatPrediction firstPrediction = predictionsForBlock.get(0);
          StopTimeIndices stopTimeIndices = _stopTimeMappings.get(new ServiceDateBlockKey(
              firstPrediction.getRouteTag(), blockId, status.getServiceDate()));
          if (stopTimeIndices != null) {
            applyStopTimeIndicesToPredictions(predictionsForBlock, status,
                stopTimeIndices);
          }
        }
      }
    }
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

  private void updateTimeZone(GtfsRelationalDao dao) {
    for (Agency agency : dao.getAllAgencies()) {
      if (agency.getTimezone() == null)
        continue;
      _agencyTimeZone = TimeZone.getTimeZone(agency.getTimezone());
      return;
    }
  }

  private VehicleStatus updateVehicleStatus(String vehicleId) {
    VehicleStatus status = _vehicleStatusById.get(vehicleId);
    if (status == null) {
      Calendar c = Calendar.getInstance(_agencyTimeZone);
      ServiceDate serviceDate = new ServiceDate(c);
      Date asDate = serviceDate.getAsDate(_agencyTimeZone);
      status = new VehicleStatus(serviceDate, asDate.getTime());
      _vehicleStatusById.put(vehicleId, status);
    }
    status.touch();
    return status;
  }

  public void applyStopTimeIndicesToPredictions(
      List<FlatPrediction> predictions, VehicleStatus status,
      StopTimeIndices stopTimeIndices) {
    for (int predictionIndex = 0; predictionIndex < predictions.size(); ++predictionIndex) {
      FlatPrediction prediction = predictions.get(predictionIndex);
      StopTimeIndex index = stopTimeIndices.getIndexForStop(prediction.getStopTag());
      if (index == null) {
        continue;
      }
      int effectiveTime = (int) ((prediction.getEpochTime() - status.getServiceDateValue()) / 1000);
      int[] stopTimeArray = index.getStopTimes();
      int i = Arrays.binarySearch(stopTimeArray, effectiveTime);
      if (i < 0) {
        i = -(i + 1);
      }
      Min<Integer> m = new Min<Integer>();
      for (int j = Math.max(0, i - 1); j < Math.min(i + 1, stopTimeArray.length); ++j) {
        int scheduleDeviation = effectiveTime - stopTimeArray[j];
        /**
         * We're going to penalize more for being early than being late.
         */
        int scheduleDeviationFactor = scheduleDeviation < 0 ? 2 : 1;
        /**
         * Score is a function of how much the schedule deviation has changed
         * since the previous
         */
        double score = Math.abs(scheduleDeviation
            - status.getLastScheduleDeviation())
            + scheduleDeviationFactor * Math.abs(scheduleDeviation);
        m.add(score, j);
      }
      if (m.isEmpty()) {
        continue;
      }
      i = m.getMinElement();

      int scheduleDeviation = effectiveTime - stopTimeArray[i];
      status.setLastScheduleDeviation(scheduleDeviation);

      int[] indices = index.getIndices();
      int indexIntoAllStopTimes = indices[i];
      List<StopTime> stopTimes = stopTimeIndices.getStopTimes();
      StopTime stopTime = stopTimes.get(indexIntoAllStopTimes);
      prediction.setTripTag(stopTime.getTrip().getId().getId());

      for (int nextPredictionIndex = predictionIndex + 1; nextPredictionIndex < predictions.size(); ++nextPredictionIndex) {
        FlatPrediction nextPrediction = predictions.get(nextPredictionIndex);
        indexIntoAllStopTimes = getNextStopTimeIndexWithStopId(stopTimes,
            indexIntoAllStopTimes + 1, nextPrediction.getStopTag());
        if (indexIntoAllStopTimes == stopTimes.size()) {
          break;
        }
        StopTime nextStopTime = stopTimes.get(indexIntoAllStopTimes);
        nextPrediction.setTripTag(nextStopTime.getTrip().getId().getId());
      }
      return;
    }
  }

  private int getNextStopTimeIndexWithStopId(List<StopTime> stopTimes,
      int currentIndex, String stopTag) {
    while (currentIndex < stopTimes.size()) {
      StopTime stopTime = stopTimes.get(currentIndex);
      if (stopTime.getStop().getId().getId().equals(stopTag)) {
        break;
      }
      currentIndex++;
    }
    return currentIndex;
  }

  private static class VehicleStatus {

    private final ServiceDate _serviceDate;

    private final long _serviceDateValue;

    private long _lastUpdateTime;

    private int _lastScheduleDeviation;

    public VehicleStatus(ServiceDate serviceDate, long serviceDateValue) {
      _serviceDate = serviceDate;
      _serviceDateValue = serviceDateValue;
    }

    public ServiceDate getServiceDate() {
      return _serviceDate;
    }

    public long getServiceDateValue() {
      return _serviceDateValue;
    }

    public int getLastScheduleDeviation() {
      return _lastScheduleDeviation;
    }

    public void setLastScheduleDeviation(int lastScheduleDeviation) {
      _lastScheduleDeviation = lastScheduleDeviation;
    }

    public void touch() {
      _lastUpdateTime = System.currentTimeMillis();
    }

  }
}
