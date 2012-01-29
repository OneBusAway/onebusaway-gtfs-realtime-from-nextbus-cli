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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.GZIPInputStream;

import javax.inject.Singleton;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All calls to the NextBus API go through this class, which keeps a running tab
 * on our downloading throughput to make sure we don't exceed the bandwidth
 * limit set for the API.
 * 
 * @author bdferris
 */
@Singleton
public class DownloaderService {

  private static final Logger _log = LoggerFactory.getLogger(DownloaderService.class);

  private DefaultHttpClient _client = new DefaultHttpClient();

  private Deque<DownloadRecord> _downloaded = new ArrayDeque<DownloadRecord>();

  /**
   * Time, in seconds
   */
  private int _throttleWindow = 20;

  private long _throttleSize = 2 * 1024 * 1024;

  private long _totalContentLength;

  public synchronized InputStream openUrl(String uri) throws IOException {

    stallIfNeeded();

    HttpUriRequest request = new HttpGet(uri);
    request.addHeader("Accept-Encoding", "gzip");
    HttpResponse response = _client.execute(request);
    HttpEntity entity = response.getEntity();

    noteDownload(entity);

    InputStream in = entity.getContent();
    Header contentEncoding = response.getFirstHeader("Content-Encoding");
    if (contentEncoding != null
        && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
      in = new GZIPInputStream(in);
    }
    return in;
  }

  private void noteDownload(HttpEntity entity) {
    long contentLength = entity.getContentLength();
    _downloaded.add(new DownloadRecord(System.currentTimeMillis(),
        contentLength));
    _totalContentLength += contentLength;
  }

  private void stallIfNeeded() {
    long pruneIfOlderThan = System.currentTimeMillis() - _throttleWindow * 1000;
    while (!_downloaded.isEmpty()) {
      DownloadRecord record = _downloaded.peek();
      if (record.getTimestamp() < pruneIfOlderThan) {
        _totalContentLength -= record.getContentLength();
        _downloaded.poll();
      } else {
        break;
      }
    }

    if (_downloaded.isEmpty())
      return;

    long estimatedSize = _totalContentLength + 2
        * (_totalContentLength / _downloaded.size());

    if (estimatedSize <= _throttleSize)
      return;

    double toDownload = estimatedSize - _throttleSize;
    long delay = (long) ((_throttleSize / toDownload) * _throttleWindow * 1000);
    _log.info("thottling: delay=" + delay);
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
    }
  }

  private static class DownloadRecord {

    private final long timestamp;

    private final long contentLength;

    public DownloadRecord(long timestamp, long contentLength) {
      this.timestamp = timestamp;
      this.contentLength = contentLength;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public long getContentLength() {
      return contentLength;
    }
  }
}
