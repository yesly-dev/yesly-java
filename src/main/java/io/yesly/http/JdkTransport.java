package io.yesly.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** The default transport, on {@link java.net.http.HttpClient}. */
public final class JdkTransport implements HttpTransport {
  private final HttpClient http;

  public JdkTransport() {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
  }

  public JdkTransport(HttpClient http) {
    this.http = http;
  }

  @Override
  public Response send(String method, String url, Map<String, String> headers, String body, Duration timeout) throws IOException, InterruptedException {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
    headers.forEach(b::header);
    b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    Map<String, List<String>> h = new TreeMap<>();
    res.headers().map().forEach((k, v) -> h.put(k.toLowerCase(Locale.ROOT), v));
    return new Response(res.statusCode(), h, res.body());
  }
}
