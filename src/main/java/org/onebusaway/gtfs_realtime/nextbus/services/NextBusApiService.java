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
package org.onebusaway.gtfs_realtime.nextbus.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.digester.Digester;
import org.onebusaway.gtfs_realtime.nextbus.model.RouteStopCoverage;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBDirection;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBPrediction;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBPredictions;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBRoute;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBStop;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBStopTime;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBTrip;
import org.onebusaway.gtfs_realtime.nextbus.model.api.NBVehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

@Singleton
public class NextBusApiService {

  private static final Logger _log = LoggerFactory.getLogger(NextBusApiService.class);

  private DownloaderService _downloader;

  private String _agencyId;

  private Digester _digester = getDigester();

  private File _cacheDirectory;

  @Inject
  public void setDownloader(DownloaderService downloader) {
    _downloader = downloader;
  }

  public void setAgencyId(String agencyId) {
    _agencyId = agencyId;
  }

  public void setCacheDirectory(File cacheDirectory) {
    _cacheDirectory = cacheDirectory;
  }

  @SuppressWarnings("unchecked")
  public List<NBRoute> downloadRouteList() throws IOException {
    String url = getBaseUrl() + "/service/publicXMLFeed?command=routeList&a="
        + _agencyId;
    return (List<NBRoute>) digestUrl(url, true);
  }

  @SuppressWarnings("unchecked")
  public List<NBRoute> downloadRouteConfigList(String routeTag)
      throws IOException {
    String url = getBaseUrl() + "/service/publicXMLFeed?command=routeConfig&a="
        + _agencyId + "&r=" + routeTag;
    return (List<NBRoute>) digestUrl(url, true);
  }

  @SuppressWarnings("unchecked")
  public List<NBRoute> downloadRouteScheduleList(String routeTag)
      throws IOException {
    String url = getBaseUrl() + "/service/publicXMLFeed?command=schedule&a="
        + _agencyId + "&r=" + routeTag;
    return (List<NBRoute>) digestUrl(url, true);
  }

  @SuppressWarnings("unchecked")
  public List<NBPredictions> downloadPredictions(RouteStopCoverage coverage)
      throws IOException {
    String url = getBaseUrl() + "/service/publicXMLFeed?command=predictionsForMultiStops&a="
        + _agencyId;
    for (String stopTag : coverage.getStopTags()) {
      url += "&stops=" + coverage.getRouteTag() + "%7c" + stopTag;
    }
    return (List<NBPredictions>) digestUrl(url, false);
  }

  @SuppressWarnings("unchecked")
  public List<NBVehicle> downloadVehicleLocations(String routeTag, long prevRequestTime)
      throws IOException {
    String url = getBaseUrl() + "/service/publicXMLFeed?command=vehicleLocations&a="
        + _agencyId + "&r=" + routeTag;
    if (prevRequestTime != 0) {
      url += "&t=" + prevRequestTime;
    }
    return (List<NBVehicle>) digestUrl(url, false);
  }

  public String getBaseUrl() {
    if (System.getProperty("nextbus.url") != null)
      return System.getProperty("nextbus.url");
    return "http://webservices.nextbus.com";
  }

  private Object digestUrl(String url, boolean cache) throws IOException {
    File cacheFile = getCacheFileForUrl(url);
    if (cache && cacheFile != null && cacheFile.exists()) {
      ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
          new FileInputStream(cacheFile)));
      try {
        Object object = ois.readObject();
        ois.close();
        return object;
      } catch (ClassNotFoundException ex) {
        try {
          ois.close();
        } catch (IOException ex2) {

        }
        throw new IllegalStateException(ex);
      }
    }
    InputStream in = _downloader.openUrl(url);
    Object result = safeDigest(in);
    if (cache && cacheFile != null) {
      ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(
          new FileOutputStream(cacheFile)));
      oos.writeObject(result);
      oos.close();
    }
    return result;
  }

  private Object safeDigest(InputStream in) throws IOException {
    try {
      return _digester.parse(in);
    } catch (Exception ex) {
      _log.error("Error digesting: " + ex.toString());
      return null;
    }
    finally {
      in.close();
    }
  }

  private File getCacheFileForUrl(String url)
      throws UnsupportedEncodingException {
    if (_cacheDirectory == null) {
      return null;
    }
    try {
      MessageDigest cript = MessageDigest.getInstance("SHA-1");
      cript.reset();
      cript.update(url.getBytes("utf8"));
      String name = new String(Hex.encodeHex(cript.digest()));
      return new File(_cacheDirectory, name);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private Digester getDigester() {
    Digester digester = new Digester();
    digester.addObjectCreate("body", ArrayList.class);

    digester.addObjectCreate("body/route", NBRoute.class);
    digester.addSetProperties("body/route");
    digester.addSetNext("body/route", "add");

    digester.addObjectCreate("body/route/stop", NBStop.class);
    digester.addSetProperties("body/route/stop");
    digester.addSetNext("body/route/stop", "addStop");

    digester.addObjectCreate("body/route/direction", NBDirection.class);
    digester.addSetProperties("body/route/direction");
    digester.addSetNext("body/route/direction", "addDirection");

    digester.addObjectCreate("body/route/direction/stop", NBStop.class);
    digester.addSetProperties("body/route/direction/stop");
    digester.addSetNext("body/route/direction/stop", "addStop");

    digester.addObjectCreate("body/route/tr", NBTrip.class);
    digester.addSetProperties("body/route/tr");
    digester.addSetNext("body/route/tr", "addTrip");

    digester.addObjectCreate("body/route/tr/stop", NBStopTime.class);
    digester.addSetProperties("body/route/tr/stop");
    digester.addSetNext("body/route/tr/stop", "addStopTime");

    digester.addObjectCreate("body/predictions", NBPredictions.class);
    digester.addSetProperties("body/predictions");
    digester.addSetNext("body/predictions", "add");

    digester.addObjectCreate("body/predictions/direction", NBDirection.class);
    digester.addSetProperties("body/predictions/direction");
    digester.addSetNext("body/predictions/direction", "addDirection");

    digester.addObjectCreate("body/predictions/direction/prediction",
        NBPrediction.class);
    digester.addSetProperties("body/predictions/direction/prediction");
    digester.addSetNext("body/predictions/direction/prediction",
        "addPrediction");
    
    digester.addObjectCreate("body/vehicle", NBVehicle.class);
    digester.addSetProperties("body/vehicle");
    digester.addSetNext("body/vehicle", "add");

    return digester;
  }
}
