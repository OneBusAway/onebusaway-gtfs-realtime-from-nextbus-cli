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
package org.onebusaway.gtfs_realtime.nextbus.services;

public class TripUpdateId {
  private final String vehicleId;

  private final String tripId;

  public TripUpdateId(String vehicleId, String tripId) {
    if (vehicleId == null) {
      throw new IllegalArgumentException();
    }
    this.vehicleId = vehicleId;
    this.tripId = tripId;
  }

  public String getVehicleId() {
    return vehicleId;
  }

  public String getTripId() {
    return tripId;
  }

  public String getFeedEntityId() {
    StringBuilder b = new StringBuilder();
    b.append("v=");
    b.append(vehicleId);
    if (tripId != null) {
      b.append(",t=");
      b.append(tripId);
    }
    return b.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((tripId == null) ? 0 : tripId.hashCode());
    result = prime * result + vehicleId.hashCode();
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
    TripUpdateId other = (TripUpdateId) obj;
    if (tripId == null) {
      if (other.tripId != null)
        return false;
    } else if (!tripId.equals(other.tripId))
      return false;
    return vehicleId.equals(other.vehicleId);
  }

}
