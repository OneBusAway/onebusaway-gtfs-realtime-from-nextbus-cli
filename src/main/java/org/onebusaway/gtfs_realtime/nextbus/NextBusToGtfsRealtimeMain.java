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
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.onebusaway.cli.CommandLineInterfaceLibrary;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeFileWriter;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeServlet;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeSource;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.TripUpdates;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.VehiclePositions;
import org.onebusaway.gtfs_realtime.nextbus.services.NextBusApiService;
import org.onebusaway.gtfs_realtime.nextbus.services.NextBusToGtfsRealtimeService;
import org.onebusaway.gtfs_realtime.nextbus.services.NextBusToGtfsService;
import org.onebusaway.guice.jsr250.LifecycleService;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class NextBusToGtfsRealtimeMain {

  private static final String ARG_AGENCY_ID = "agencyId";

  private static final String ARG_TRIP_UPDATES_PATH = "tripUpdatesPath";

  private static final String ARG_TRIP_UPDATES_URL = "tripUpdatesUrl";

  private static final String ARG_VEHICLE_POSITIONS_PATH = "vehiclePositionsPath";

  private static final String ARG_VEHICLE_POSITIONS_URL = "vehiclePositionsUrl";

  private static final String ARG_CACHE_DIR = "cacheDir";

  private static final String ARG_GTFS_PATH = "gtfsPath";

  private static final String ARG_GTFS_TRIP_MATCHING = "gtfsTripMatching";

  public static void main(String[] args) throws Exception {
    NextBusToGtfsRealtimeMain m = new NextBusToGtfsRealtimeMain();
    m.run(args);
  }

  private NextBusApiService _nextBusApiService;

  private NextBusToGtfsService _matchingService;

  private GtfsRealtimeSource _tripUpdatesSource;

  private GtfsRealtimeSource _vehiclePositionsSource;

  private NextBusToGtfsRealtimeService _nextBusToGtfsRealtimeService;

  private LifecycleService _lifecycleService;

  @Inject
  public void setNextBusApiService(NextBusApiService nextBusApiService) {
    _nextBusApiService = nextBusApiService;
  }

  @Inject
  public void setMatchingService(NextBusToGtfsService matchingService) {
    _matchingService = matchingService;
  }

  @Inject
  public void setTripUpdatesSource(@TripUpdates
  GtfsRealtimeSource tripUpdatesSource) {
    _tripUpdatesSource = tripUpdatesSource;
  }

  @Inject
  public void setVehiclePositionsSource(@VehiclePositions
  GtfsRealtimeSource vehiclePositionsSource) {
    _vehiclePositionsSource = vehiclePositionsSource;
  }

  @Inject
  public void setNextBusToGtfsRealtimeService(
      NextBusToGtfsRealtimeService nextBusToGtfsRealtimeService) {
    _nextBusToGtfsRealtimeService = nextBusToGtfsRealtimeService;
  }

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

    Set<Module> modules = new HashSet<Module>();
    NextBusToGtfsRealtimeModule.addModuleAndDependencies(modules);

    Injector injector = Guice.createInjector(modules);
    injector.injectMembers(this);

    String nextBusAgencyId = cli.getOptionValue(ARG_AGENCY_ID);
    _nextBusApiService.setAgencyId(nextBusAgencyId);

    if (cli.hasOption(ARG_TRIP_UPDATES_URL)) {
      GtfsRealtimeServlet servlet = injector.getInstance(GtfsRealtimeServlet.class);
      servlet.setSource(_tripUpdatesSource);
      servlet.setUrl(new URL(cli.getOptionValue(ARG_TRIP_UPDATES_URL)));
      _nextBusToGtfsRealtimeService.setEnableTripUpdates(true);
    }
    if (cli.hasOption(ARG_TRIP_UPDATES_PATH)) {
      GtfsRealtimeFileWriter fileWriter = injector.getInstance(GtfsRealtimeFileWriter.class);
      fileWriter.setSource(_tripUpdatesSource);
      fileWriter.setPath(new File(cli.getOptionValue(ARG_TRIP_UPDATES_PATH)));
      _nextBusToGtfsRealtimeService.setEnableTripUpdates(true);
    }

    if (cli.hasOption(ARG_VEHICLE_POSITIONS_URL)) {
      GtfsRealtimeServlet servlet = injector.getInstance(GtfsRealtimeServlet.class);
      servlet.setSource(_vehiclePositionsSource);
      servlet.setUrl(new URL(cli.getOptionValue(ARG_VEHICLE_POSITIONS_URL)));
      _nextBusToGtfsRealtimeService.setEnableVehiclePositions(true);
    }
    if (cli.hasOption(ARG_VEHICLE_POSITIONS_PATH)) {
      GtfsRealtimeFileWriter fileWriter = injector.getInstance(GtfsRealtimeFileWriter.class);
      fileWriter.setSource(_vehiclePositionsSource);
      fileWriter.setPath(new File(
          cli.getOptionValue(ARG_VEHICLE_POSITIONS_PATH)));
      _nextBusToGtfsRealtimeService.setEnableVehiclePositions(true);
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
    options.addOption(ARG_VEHICLE_POSITIONS_PATH, true,
        "vehicle positions path");
    options.addOption(ARG_VEHICLE_POSITIONS_URL, true, "vehicle positions url");
    options.addOption(ARG_CACHE_DIR, true, "route configuration cache path");
    options.addOption(ARG_GTFS_PATH, true, "gtfs path");
    options.addOption(ARG_GTFS_TRIP_MATCHING, false,
        "enable gtfs trip matching");
  }
}
