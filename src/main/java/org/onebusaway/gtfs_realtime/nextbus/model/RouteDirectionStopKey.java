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

public class RouteDirectionStopKey {
  private final String routeTag;
  private final String directionTag;
  private final String stopTag;

  public RouteDirectionStopKey(String routeTag, String directionTag,
      String stopTag) {
    this.routeTag = routeTag;
    this.directionTag = directionTag;
    this.stopTag = stopTag;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + directionTag.hashCode();
    result = prime * result + routeTag.hashCode();
    result = prime * result + stopTag.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    RouteDirectionStopKey other = (RouteDirectionStopKey) obj;
    if (!directionTag.equals(other.directionTag))
      return false;
    if (!routeTag.equals(other.routeTag))
      return false;
    if (!stopTag.equals(other.stopTag))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return routeTag + " " + directionTag + " " + stopTag;
  }
}