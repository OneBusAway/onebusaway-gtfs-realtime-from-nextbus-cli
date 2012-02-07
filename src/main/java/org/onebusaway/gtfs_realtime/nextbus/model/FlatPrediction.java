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
