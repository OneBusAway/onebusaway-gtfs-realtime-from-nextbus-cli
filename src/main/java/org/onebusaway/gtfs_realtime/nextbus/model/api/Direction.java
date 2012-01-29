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

public class Direction implements Serializable {

  private static final long serialVersionUID = 1L;

  private String tag;

  private String title;

  private String name;

  private boolean useForUI;

  private List<Stop> stops = new ArrayList<Stop>();

  private List<Prediction> predictions = new ArrayList<Prediction>();

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isUseForUI() {
    return useForUI;
  }

  public void setUseForUI(boolean useForUI) {
    this.useForUI = useForUI;
  }

  public void addStop(Stop stop) {
    stops.add(stop);
  }

  public List<Stop> getStops() {
    return stops;
  }

  public void addPrediction(Prediction prediction) {
    predictions.add(prediction);
  }

  public List<Prediction> getPredictions() {
    return predictions;
  }
}
