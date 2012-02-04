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

public class NBRoute implements Serializable {

  private static final long serialVersionUID = 1L;

  private String tag;

  private String title;

  private String color;

  private String oppositeColor;

  private double latMin;

  private double lonMin;

  private double latMax;

  private double longMax;

  private String scheduleClass;

  private String serviceClass;

  private String direction;

  private List<NBStop> stops = new ArrayList<NBStop>();

  private List<NBDirection> directions = new ArrayList<NBDirection>();

  private List<NBTrip> trips = new ArrayList<NBTrip>();

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public String getOppositeColor() {
    return oppositeColor;
  }

  public void setOppositeColor(String oppositeColor) {
    this.oppositeColor = oppositeColor;
  }

  public double getLatMin() {
    return latMin;
  }

  public void setLatMin(double latMin) {
    this.latMin = latMin;
  }

  public double getLonMin() {
    return lonMin;
  }

  public void setLonMin(double lonMin) {
    this.lonMin = lonMin;
  }

  public double getLatMax() {
    return latMax;
  }

  public void setLatMax(double latMax) {
    this.latMax = latMax;
  }

  public double getLongMax() {
    return longMax;
  }

  public void setLongMax(double longMax) {
    this.longMax = longMax;
  }

  public String getScheduleClass() {
    return scheduleClass;
  }

  public void setScheduleClass(String scheduleClass) {
    this.scheduleClass = scheduleClass;
  }

  public String getServiceClass() {
    return serviceClass;
  }

  public void setServiceClass(String serviceClass) {
    this.serviceClass = serviceClass;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }

  public void addStop(NBStop stop) {
    stops.add(stop);
  }

  public List<NBStop> getStops() {
    return stops;
  }

  public void addDirection(NBDirection direction) {
    directions.add(direction);
  }

  public List<NBDirection> getDirections() {
    return directions;
  }

  public void addTrip(NBTrip trip) {
    trips.add(trip);
  }

  public List<NBTrip> getTrips() {
    return trips;
  }

  @Override
  public String toString() {
    return tag;
  }
}
