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

public class FlatPrediction implements Comparable<FlatPrediction> {
  private String routeTag;

  private String stopTag;

  private long epochTime;

  private String dirTag;

  private String vehicle;

  private String block;

  private String tripTag;

  public String getRouteTag() {
    return routeTag;
  }

  public void setRouteTag(String routeTag) {
    this.routeTag = routeTag;
  }

  public String getStopTag() {
    return stopTag;
  }

  public void setStopTag(String stopTag) {
    this.stopTag = stopTag;
  }

  public long getEpochTime() {
    return epochTime;
  }

  public void setEpochTime(long epochTime) {
    this.epochTime = epochTime;
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

  @Override
  public int compareTo(FlatPrediction o) {
    return this.epochTime == o.epochTime ? 0 : (this.epochTime < o.epochTime
        ? -1 : 1);
  }
}
