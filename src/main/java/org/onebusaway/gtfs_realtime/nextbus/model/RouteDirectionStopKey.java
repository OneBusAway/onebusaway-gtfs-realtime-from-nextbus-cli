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