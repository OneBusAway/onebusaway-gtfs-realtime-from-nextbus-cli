/**
 * Copyright (C) 2013 Google, Inc.
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

public class NBVehicle {

  private String id;

  private String routeTag;

  private String dirTag;

  private double lat;

  private double lon;

  private int secsSinceReport;

  private boolean predictable;

  private int heading;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getRouteTag() {
    return routeTag;
  }

  public void setRouteTag(String routeTag) {
    this.routeTag = routeTag;
  }

  public String getDirTag() {
    return dirTag;
  }

  public void setDirTag(String dirTag) {
    this.dirTag = dirTag;
  }

  public double getLat() {
    return lat;
  }

  public void setLat(double lat) {
    this.lat = lat;
  }

  public double getLon() {
    return lon;
  }

  public void setLon(double lon) {
    this.lon = lon;
  }

  public int getSecsSinceReport() {
    return secsSinceReport;
  }

  public void setSecsSinceReport(int secsSinceReport) {
    this.secsSinceReport = secsSinceReport;
  }

  public boolean isPredictable() {
    return predictable;
  }

  public void setPredictable(boolean predictable) {
    this.predictable = predictable;
  }

  public int getHeading() {
    return heading;
  }

  public void setHeading(int heading) {
    this.heading = heading;
  }
}
