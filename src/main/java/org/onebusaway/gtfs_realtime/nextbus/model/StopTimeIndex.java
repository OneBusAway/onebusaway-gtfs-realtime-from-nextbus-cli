package org.onebusaway.gtfs_realtime.nextbus.model;

public class StopTimeIndex {
  private final int[] stopTimes;

  private final int[] indices;

  public StopTimeIndex(int[] stopTimes, int[] indices) {
    this.stopTimes = stopTimes;
    this.indices = indices;
  }

  public int[] getStopTimes() {
    return stopTimes;
  }

  public int[] getIndices() {
    return indices;
  }
}