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
package org.onebusaway.gtfs_realtime.nextbus.model.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class NBTrip implements Serializable {

  private static final long serialVersionUID = 1L;

  private String blockID;

  private String direction;

  private List<NBStopTime> stopTimes = new ArrayList<NBStopTime>();

  public String getBlockID() {
    return blockID;
  }

  public void setBlockID(String blockId) {
    this.blockID = blockId;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }

  public List<NBStopTime> getStopTimes() {
    return stopTimes;
  }

  public void addStopTime(NBStopTime stopTime) {
    stopTimes.add(stopTime);
  }

  public void setStopTimes(List<NBStopTime> stopTimes) {
    this.stopTimes = stopTimes;
  }

  @Override
  public String toString() {
    return blockID + " " + direction + " " + stopTimes;
  }
}
