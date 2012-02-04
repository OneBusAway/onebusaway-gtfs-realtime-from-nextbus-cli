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
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class NBStopTime implements Serializable {

  private static NumberFormat _format = new DecimalFormat("00");

  private static final long serialVersionUID = 1L;

  private String tag;

  private int epochTime;

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public int getEpochTime() {
    return epochTime;
  }

  public void setEpochTime(int epochTime) {
    this.epochTime = epochTime;
  }

  @Override
  public String toString() {
    int sec = epochTime / 1000;
    int hours = sec / (3600);
    sec = sec % 3600;
    int mins = sec / 60;
    sec = mins % 60;
    return tag + " " + _format.format(hours) + ":" + _format.format(mins) + ":"
        + _format.format(sec);
  }
}
