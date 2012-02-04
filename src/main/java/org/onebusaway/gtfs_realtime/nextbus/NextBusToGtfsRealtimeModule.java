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
package org.onebusaway.gtfs_realtime.nextbus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.onebusaway.gtfs_realtime.nextbus.services.DownloaderService;
import org.onebusaway.gtfs_realtime.nextbus.services.NextBusToGtfsRealtimeService;
import org.onebusaway.gtfs_realtime.nextbus.services.NextBusToGtfsService;
import org.onebusaway.gtfs_realtime.nextbus.services.RouteStopCoverageService;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeProvider;

import com.google.inject.AbstractModule;

public class NextBusToGtfsRealtimeModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(DownloaderService.class);
    bind(RouteStopCoverageService.class);
    bind(NextBusToGtfsRealtimeService.class);
    bind(GtfsRealtimeProvider.class).to(NextBusToGtfsRealtimeService.class);
    bind(NextBusToGtfsService.class);
    bind(ScheduledExecutorService.class).toInstance(
        Executors.newSingleThreadScheduledExecutor());
  }
}
