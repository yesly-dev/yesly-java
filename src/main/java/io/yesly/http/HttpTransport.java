package io.yesly.http;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Sends one HTTP request. Replace it for tests or proxies. */
public interface HttpTransport {
  /** Status, headers (lower-case names) and body of an answer. */
  final class Response {
    public final int status;
    public final Map<String, List<String>> headers;
    public final String body;

    public Response(int status, Map<String, List<String>> headers, String body) {
      this.status = status;
      this.headers = headers;
      this.body = body;
    }
  }

  /** Throws {@link IOException} when there is no answer. */
  Response send(String method, String url, Map<String, String> headers, String body, Duration timeout) throws IOException, InterruptedException;
}
