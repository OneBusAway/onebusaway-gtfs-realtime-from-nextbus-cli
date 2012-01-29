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

public class Prediction implements Serializable {

  private static final long serialVersionUID = 1L;

  private long epochTime;

  private int seconds;

  private int minutes;

  private boolean affectedByLayover;

  private String dirTag;

  private String vehicle;

  private String block;

  private String tripTag;

  private String stopTag;

  public long getEpochTime() {
    return epochTime;
  }

  public void setEpochTime(long epochTime) {
    this.epochTime = epochTime;
  }

  public int getSeconds() {
    return seconds;
  }

  public void setSeconds(int seconds) {
    this.seconds = seconds;
  }

  public int getMinutes() {
    return minutes;
  }

  public void setMinutes(int minutes) {
    this.minutes = minutes;
  }

  public boolean isAffectedByLayover() {
    return affectedByLayover;
  }

  public void setAffectedByLayover(boolean affectedByLayover) {
    this.affectedByLayover = affectedByLayover;
  }

  public String getDirTag() {
    return dirTag;
  }

  public void setDirTag(String dirTag) {
    this.dirTag = dirTag;
  }

  public String getVehicle() {
    return vehicle;
  }

  public void setVehicle(String vehicle) {
    this.vehicle = vehicle;
  }

  public String getBlock() {
    return block;
  }

  public void setBlock(String block) {
    this.block = block;
  }

  public String getTripTag() {
    return tripTag;
  }

  public void setTripTag(String tripTag) {
    this.tripTag = tripTag;
  }

  public String getStopTag() {
    return stopTag;
  }

  public void setStopTag(String stopTag) {
    this.stopTag = stopTag;
  }
}
