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

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.onebusaway.cli.CommandLineInterfaceLibrary;
import org.onebusaway.gtfs_realtime.nextbus.services.NextBusApiService;
import org.onebusaway.gtfs_realtime.nextbus.services.NextBusToGtfsService;
import org.onebusaway.gtfs_realtime.nextbus.services.RouteStopCoverageService;
import org.onebusaway.guice.jetty_exporter.JettyExporterModule;
import org.onebusaway.guice.jsr250.JSR250Module;
import org.onebusaway.guice.jsr250.LifecycleService;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeExporterModule;
import org.onebusway.gtfs_realtime.exporter.TripUpdatesFileWriter;
import org.onebusway.gtfs_realtime.exporter.TripUpdatesServlet;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class NextBusToGtfsRealtimeMain {

  private static final String ARG_AGENCY_ID = "agencyId";

  private static final String ARG_TRIP_UPDATES_PATH = "tripUpdatesPath";

  private static final String ARG_TRIP_UPDATES_URL = "tripUpdatesUrl";

  private static final String ARG_CACHE_DIR = "cacheDir";

  private static final String ARG_GTFS_PATH = "gtfsPath";

  private static final String ARG_GTFS_TRIP_MATCHING = "gtfsTripMatching";

  public static void main(String[] args) throws Exception {
    NextBusToGtfsRealtimeMain m = new NextBusToGtfsRealtimeMain();
    m.run(args);
  }

  private NextBusApiService _nextBusApiService;

  private RouteStopCoverageService _routeStopCoverageService;

  private NextBusToGtfsService _matchingService;

  private LifecycleService _lifecycleService;

  @Inject
  public void setNextBusApiService(NextBusApiService nextBusApiService) {
    _nextBusApiService = nextBusApiService;
  }

  @Inject
  public void setRouteStopCoverageService(
      RouteStopCoverageService routeStopCoverageService) {
    _routeStopCoverageService = routeStopCoverageService;
  }

  @Inject
  public void setMatchingService(NextBusToGtfsService matchingService) {
    _matchingService = matchingService;
  }

  // @Inject
  // public void setGtfsRealtimeService(GtfsRealtimeService gtfsRealtimeService)
  // {
  // No op to make sure dependency is instantiated
  // }

  @Inject
  public void setLifecycleService(LifecycleService lifecycleService) {
    _lifecycleService = lifecycleService;
  }

  public void run(String[] args) throws Exception {

    if (args.length == 0 || CommandLineInterfaceLibrary.wantsHelp(args)) {
      printUsage();
      System.exit(-1);
    }

    Options options = new Options();
    buildOptions(options);
    Parser parser = new GnuParser();
    CommandLine cli = parser.parse(options, args);

    List<Module> modules = new ArrayList<Module>();
    modules.add(new JSR250Module());
    modules.add(new JettyExporterModule());
    modules.add(new GtfsRealtimeExporterModule());
    modules.add(new NextBusToGtfsRealtimeModule());

    Injector injector = Guice.createInjector(modules);
    injector.injectMembers(this);

    String nextBusAgencyId = cli.getOptionValue(ARG_AGENCY_ID);
    _nextBusApiService.setAgencyId(nextBusAgencyId);

    if (cli.hasOption(ARG_TRIP_UPDATES_URL)) {
      URL url = new URL(cli.getOptionValue(ARG_TRIP_UPDATES_URL));
      TripUpdatesServlet servlet = injector.getInstance(TripUpdatesServlet.class);
      servlet.setUrl(url);
    }
    if (cli.hasOption(ARG_TRIP_UPDATES_PATH)) {
      File path = new File(cli.getOptionValue(ARG_TRIP_UPDATES_PATH));
      TripUpdatesFileWriter writer = injector.getInstance(TripUpdatesFileWriter.class);
      writer.setPath(path);
    }

    if (cli.hasOption(ARG_CACHE_DIR)) {
      File cacheDir = new File(cli.getOptionValue(ARG_CACHE_DIR));
      cacheDir.mkdirs();
      _nextBusApiService.setCacheDirectory(cacheDir);
    }
    if (cli.hasOption(ARG_GTFS_PATH)) {
      _matchingService.setGtfsPath(new File(cli.getOptionValue(ARG_GTFS_PATH)));
    }
    _matchingService.setGtfsTripMatching(cli.hasOption(ARG_GTFS_TRIP_MATCHING));

    _lifecycleService.start();
  }

  private void printUsage() {
    CommandLineInterfaceLibrary.printUsage(getClass());
  }

  protected void buildOptions(Options options) {
    Option agencyIdOption = new Option(ARG_AGENCY_ID, true, "agency id");
    agencyIdOption.setRequired(true);
    options.addOption(agencyIdOption);

    options.addOption(ARG_TRIP_UPDATES_PATH, true, "trip updates path");
    options.addOption(ARG_TRIP_UPDATES_URL, true, "trip updates url");
    options.addOption(ARG_CACHE_DIR, true, "route configuration cache path");
    options.addOption(ARG_GTFS_PATH, true, "gtfs path");
    options.addOption(ARG_GTFS_TRIP_MATCHING, false,
        "enable gtfs trip matching");
  }
}
