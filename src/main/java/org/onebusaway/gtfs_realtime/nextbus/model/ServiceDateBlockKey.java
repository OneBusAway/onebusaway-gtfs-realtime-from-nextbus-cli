package org.onebusaway.gtfs_realtime.nextbus.model;

import org.onebusaway.gtfs.model.calendar.ServiceDate;

public class ServiceDateBlockKey {

  private final String route;

  private final String block;

  private final ServiceDate serviceDate;

  public ServiceDateBlockKey(String route, String block, ServiceDate serviceDate) {
    if (route == null)
      throw new IllegalArgumentException();
    if (block == null)
      throw new IllegalArgumentException();
    if (serviceDate == null)
      throw new IllegalArgumentException();
    this.route = route;
    this.block = block;
    this.serviceDate = serviceDate;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + route.hashCode();
    result = prime * result + block.hashCode();
    result = prime * result + serviceDate.hashCode();
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
    ServiceDateBlockKey other = (ServiceDateBlockKey) obj;
    if (!route.equals(other.route))
      return false;
    if (!block.equals(other.block))
      return false;
    if (!serviceDate.equals(other.serviceDate))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return route + " " + block + " " + serviceDate;
  }
}
