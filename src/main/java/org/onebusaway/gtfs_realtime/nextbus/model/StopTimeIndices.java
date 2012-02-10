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
package org.onebusaway.gtfs_realtime.nextbus.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.onebusaway.collections.FactoryMap;
import org.onebusaway.gtfs.model.StopTime;

public class StopTimeIndices {

  private final List<StopTime> stopTimes;

  private final Map<String, StopTimeIndex> indicesByStop;

  public static StopTimeIndices create(List<StopTime> stopTimes) {
    Map<StopTime, Integer> stopTimeToPosition = new HashMap<StopTime, Integer>();
    Map<String, List<StopTime>> stopTimesByStopId = new FactoryMap<String, List<StopTime>>(
        new ArrayList<StopTime>());
    for (int i = 0; i < stopTimes.size(); ++i) {
      StopTime stopTime = stopTimes.get(i);
      stopTimeToPosition.put(stopTime, i);
      stopTimesByStopId.get(stopTime.getStop().getId().getId()).add(stopTime);
    }
    Map<String, StopTimeIndex> indicesByStop = new HashMap<String, StopTimeIndex>();
    for (Map.Entry<String, List<StopTime>> entry : stopTimesByStopId.entrySet()) {
      String stopId = entry.getKey();
      List<StopTime> stopTimesForStop = entry.getValue();
      Collections.sort(stopTimesForStop);
      int[] stopTimeArray = new int[stopTimesForStop.size()];
      int[] indices = new int[stopTimesForStop.size()];
      for (int i = 0; i < stopTimeArray.length; ++i) {
        StopTime stopTime = stopTimesForStop.get(i);
        stopTimeArray[i] = (stopTime.getArrivalTime() + stopTime.getDepartureTime()) / 2;
        indices[i] = stopTimeToPosition.get(stopTime);
      }
      indicesByStop.put(stopId, new StopTimeIndex(stopTimeArray, indices));
    }
    return new StopTimeIndices(stopTimes, indicesByStop);
  }

  private StopTimeIndices(List<StopTime> stopTimes,
      Map<String, StopTimeIndex> indicesByStop) {
    this.stopTimes = stopTimes;
    this.indicesByStop = indicesByStop;
  }

  public StopTimeIndex getIndexForStop(String stopId) {
    return indicesByStop.get(stopId);
  }

  public List<StopTime> getStopTimes() {
    return stopTimes;
  }

}
