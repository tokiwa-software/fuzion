/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementaaion.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of Metrics
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Metrics extends ANY {


  /**
   * Post data to our Influx DB if INFLUXDB_TOKEN is set
   */
  public static String postToInflux(String data)
  {
    var org = "tokiwa";
    var bucket = "benchmarks";

    String token = System.getenv("INFLUXDB_TOKEN");

    if (token == null || token.trim().isEmpty())
      {
        return "INFLUXDB_TOKEN not set, not sending to influxdb.";
      }

    HttpClient client = HttpClient.newHttpClient();
    String url = String.format(
      "https://influxdb.tokiwa.software:8086/api/v2/write?org=%s&bucket=%s&precision=s",
      org, bucket);

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("Authorization", "Token " + token)
      .header("Content-Type", "text/plain")
      .POST(HttpRequest.BodyPublishers.ofString(data))
      .build();

    try
      {
        HttpResponse<String> response = client.send(
          request, HttpResponse.BodyHandlers.ofString());
        return response.body();
      }
    catch (Exception e)
      {
        return "Error sending to InfluxDB: " + e.getMessage();
      }
  }

  public static void dfaMetrics(long startTime, int preIter, int realIter, int calls, int values, String mainClazz)
  {
    var elapsedMillis = System.currentTimeMillis() - startTime;
    var data = String.format(
      "dfa,main_name=%s time=%s,pre_iter=%s,real_iter=%s,calls=%s,unique_values=%s",
      mainClazz,
      elapsedMillis,
      preIter,
      realIter,
      calls,
      values);
    postToInflux(data);
  }

  public static void fumFile(String moduleName, long frontEndMilliSeconds, long totalMilliSeconds)
  {
    var data = String.format("fum,module=%s frontend=%s,total=%s", moduleName, frontEndMilliSeconds, totalMilliSeconds);
    postToInflux(data);
  }
}
