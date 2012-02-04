package org.onebusaway.gtfs_realtime.nextbus.model;

import org.onebusaway.gtfs.serialization.mappings.StopTimeFieldMappingFactory;

public class FlatStopTime implements Comparable<FlatStopTime> {

  private String scheduleClass;

  private String serviceClass;

  private String routeTag;

  private String directionTag;

  private String blockTag;

  private int tripIndex;

  private String stopTag;

  private String gtfsStopId;

  private int epochTime;

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

  public String getRouteTag() {
    return routeTag;
  }

  public void setRouteTag(String routeTag) {
    this.routeTag = routeTag;
  }

  public String getDirectionTag() {
    return directionTag;
  }

  public void setDirectionTag(String directionTag) {
    this.directionTag = directionTag;
  }

  public String getBlockTag() {
    return blockTag;
  }

  public void setBlockTag(String blockTag) {
    this.blockTag = blockTag;
  }

  public int getTripIndex() {
    return tripIndex;
  }

  public void setTripIndex(int tripIndex) {
    this.tripIndex = tripIndex;
  }

  public String getStopTag() {
    return stopTag;
  }

  public void setStopTag(String stopTag) {
    this.stopTag = stopTag;
  }

  public String getGtfsStopId() {
    return gtfsStopId;
  }

  public void setGtfsStopId(String gtfsStopId) {
    this.gtfsStopId = gtfsStopId;
  }

  public int getEpochTime() {
    return epochTime;
  }

  public String getEpochTimeAsString() {
    return StopTimeFieldMappingFactory.getSecondsAsString(epochTime / 1000);
  }

  public void setEpochTime(int epochTime) {
    this.epochTime = epochTime;
  }

  @Override
  public int compareTo(FlatStopTime o) {
    int c = this.epochTime - o.epochTime;
    if (c != 0) {
      return c;
    }
    boolean arrivalA = this.stopTag.endsWith("_a");
    boolean arrivalB = o.stopTag.endsWith("_a");
    boolean departureA = this.stopTag.endsWith("_d");
    boolean departureB = o.stopTag.endsWith("_d");
    if (arrivalA && departureB) {
      return -1;
    } else if (arrivalB && departureA) {
      return 1;
    }
    return 0;

  }

  @Override
  public String toString() {
    return getEpochTimeAsString();
  }
}
