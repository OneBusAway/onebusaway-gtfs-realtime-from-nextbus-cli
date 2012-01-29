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

public class Predictions implements Serializable {

  private static final long serialVersionUID = 1L;

  private String agencyTitle;

  private String stopTag;

  private String stopTitle;

  private String routeTag;

  private String routeCode;

  private String routeTitle;

  private List<Direction> directions = new ArrayList<Direction>();

  public String getAgencyTitle() {
    return agencyTitle;
  }

  public void setAgencyTitle(String agencyTitle) {
    this.agencyTitle = agencyTitle;
  }

  public String getStopTag() {
    return stopTag;
  }

  public void setStopTag(String stopTag) {
    this.stopTag = stopTag;
  }

  public String getStopTitle() {
    return stopTitle;
  }

  public void setStopTitle(String stopTitle) {
    this.stopTitle = stopTitle;
  }

  public String getRouteTag() {
    return routeTag;
  }

  public void setRouteTag(String routeTag) {
    this.routeTag = routeTag;
  }

  public String getRouteCode() {
    return routeCode;
  }

  public void setRouteCode(String routeCode) {
    this.routeCode = routeCode;
  }

  public String getRouteTitle() {
    return routeTitle;
  }

  public void setRouteTitle(String routeTitle) {
    this.routeTitle = routeTitle;
  }

  public void addDirection(Direction direction) {
    directions.add(direction);
  }

  public List<Direction> getDirections() {
    return directions;
  }
}
