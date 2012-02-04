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
import org.xml.sax.SAXException;

@Singleton
public class NextBusApiService {

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
  public List<NBRoute> downloadRouteList() throws IOException, SAXException {
    String url = "http://webservices.nextbus.com/service/publicXMLFeed?command=routeList&a="
        + _agencyId;
    return (List<NBRoute>) digestUrl(url, true);
  }

  @SuppressWarnings("unchecked")
  public List<NBRoute> downloadRouteConfigList(String routeTag)
      throws IOException, SAXException {
    String url = "http://webservices.nextbus.com/service/publicXMLFeed?command=routeConfig&a="
        + _agencyId + "&r=" + routeTag;
    return (List<NBRoute>) digestUrl(url, true);
  }

  @SuppressWarnings("unchecked")
  public List<NBRoute> downloadRouteScheduleList(String routeTag)
      throws IOException, SAXException {
    String url = "http://webservices.nextbus.com/service/publicXMLFeed?command=schedule&a="
        + _agencyId + "&r=" + routeTag;
    return (List<NBRoute>) digestUrl(url, true);
  }

  @SuppressWarnings("unchecked")
  public List<NBPredictions> downloadPredictions(RouteStopCoverage coverage)
      throws IOException, SAXException {
    String url = "http://webservices.nextbus.com/service/publicXMLFeed?command=predictionsForMultiStops&a="
        + _agencyId;
    for (String stopTag : coverage.getStopTags()) {
      url += "&stops=" + coverage.getRouteTag() + "%7c" + stopTag;
    }
    return (List<NBPredictions>) digestUrl(url, false);
  }

  private Object digestUrl(String url, boolean cache) throws IOException,
      SAXException {
    File cacheFile = getCacheFileForUrl(url);
    if (cache && cacheFile != null && cacheFile.exists()) {
      ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
          new FileInputStream(cacheFile)));
      try {
        Object object = ois.readObject();
        ois.close();
        return object;
      } catch (ClassNotFoundException ex) {
        throw new IllegalStateException(ex);
      }
    }
    InputStream in = _downloader.openUrl(url);
    Object result = _digester.parse(in);
    if (cache && cacheFile != null) {
      ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(
          new FileOutputStream(cacheFile)));
      oos.writeObject(result);
      oos.close();
    }
    return result;
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

    return digester;
  }
}
