package io.yesly;

import com.fasterxml.jackson.databind.JsonNode;
import io.yesly.exception.ApiConnectionException;
import io.yesly.exception.YeslyException;
import io.yesly.http.HttpTransport;
import io.yesly.http.JdkTransport;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** The HTTP core: authentication, retries, errors and pagination. Use {@link Yesly}. */
public class Client {
  private final String apiKey;
  private final String baseUrl;
  private final String apiVersion;
  private final Duration timeout;
  private final int maxRetries;
  private final HttpTransport transport;

  protected Client(ClientOptions o) {
    this.apiKey = o.apiKey != null ? o.apiKey : env("YESLY_API_KEY");
    String base = o.baseUrl != null ? o.baseUrl : env("YESLY_BASE_URL");
    this.baseUrl = checkBaseUrl((base != null ? base : ApiVersion.BASE_URL).replaceAll("/+$", ""));
    this.apiVersion = o.apiVersion != null ? o.apiVersion : ApiVersion.CURRENT;
    this.timeout = o.timeout;
    this.maxRetries = o.maxRetries;
    this.transport = o.transport != null ? o.transport : new JdkTransport();
  }

  /** {@code live} or {@code test}, from the key's prefix; {@code null} without a key. */
  public String mode() {
    if (apiKey != null && apiKey.startsWith("ysk_live_")) return "live";
    if (apiKey != null && apiKey.startsWith("ysk_test_")) return "test";
    return null;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  /** Sends a request and maps the answer to {@code type}. */
  public <T> T request(String method, String path, Object query, Object body, boolean idempotent, RequestOptions options, Class<T> type) {
    JsonNode json = send(method, path, query, body, idempotent, options);
    return json == null || json.isNull() ? null : Json.convert(json, type);
  }

  /** A list request; the page can be iterated across pages. */
  public <T> Page<T> list(String method, String path, Object query, RequestOptions options, Class<T> type) {
    return list(method, path, query, options, type, null);
  }

  private <T> Page<T> list(String method, String path, Object query, RequestOptions options, Class<T> type, String startingAfter) {
    Map<String, Object> q = toMap(query);
    if (startingAfter != null) q.put("starting_after", startingAfter);
    JsonNode json = send(method, path, q, null, false, options);
    List<T> data = new ArrayList<>();
    String lastId = null;
    for (JsonNode item : json.path("data")) {
      data.add(Json.convert(item, type));
      lastId = item.path("id").isTextual() ? item.path("id").asText() : null;
    }
    return new Page<>(json.path("url").asText(""), json.path("has_more").asBoolean(false), data, lastId, after -> list(method, path, query, options, type, after));
  }

  private JsonNode send(String method, String path, Object query, Object body, boolean idempotent, RequestOptions options) {
    if (apiKey == null) throw new YeslyException("No API key. Pass one to new Yesly(...) or set YESLY_API_KEY.");
    String url = baseUrl + path + queryString(toMap(query));
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Bearer " + apiKey);
    headers.put("Accept", "application/json");
    headers.put("Yesly-Version", apiVersion);
    headers.put("User-Agent", "yesly-java/" + Version.VERSION + " java/" + System.getProperty("java.version"));
    String payload = body != null ? Json.write(body) : method.equals("POST") ? "{}" : null;
    if (payload != null) headers.put("Content-Type", "application/json");
    if (idempotent) {
      String key = options != null && options.idempotencyKey != null ? options.idempotencyKey : UUID.randomUUID().toString();
      headers.put("Idempotency-Key", key);
    }
    Duration t = options != null && options.timeout != null ? options.timeout : timeout;
    int retries = options != null && options.maxRetries != null ? options.maxRetries : maxRetries;

    for (int attempt = 0; ; attempt++) {
      HttpTransport.Response res;
      try {
        res = transport.send(method, url, headers, payload, t);
      } catch (IOException e) {
        if (attempt < retries) {
          sleep(backoff(attempt));
          continue;
        }
        boolean timedOut = e instanceof HttpTimeoutException;
        throw new ApiConnectionException(timedOut ? "The request timed out after " + t.toMillis() + " ms." : "Could not reach the Yesly API.", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ApiConnectionException("The request was interrupted.", e);
      }
      JsonNode json = res.body == null || res.body.isEmpty() ? null : Json.read(res.body);
      if (res.status >= 200 && res.status < 300) return json;
      boolean retryable = res.status == 408 || res.status == 429 || res.status >= 500;
      if (retryable && attempt < retries) {
        Double after = retryAfter(res.headers);
        sleep(after != null ? after : backoff(attempt));
        continue;
      }
      throw YeslyException.fromResponse(res.status, json, res.headers);
    }
  }

  /** Waits before a retry. */
  protected void sleep(double seconds) {
    try {
      Thread.sleep((long) (seconds * 1000));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Encodes a path segment. */
  public static String encode(String segment) {
    return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toMap(Object query) {
    if (query == null) return new LinkedHashMap<>();
    if (query instanceof Map) return new LinkedHashMap<>((Map<String, Object>) query);
    return Json.MAPPER.convertValue(query, LinkedHashMap.class);
  }

  /** {@code a=1&metadata[k]=v}; nulls are left out. */
  static String queryString(Map<String, Object> query) {
    StringJoiner out = new StringJoiner("&");
    for (Map.Entry<String, Object> e : query.entrySet()) {
      Object v = e.getValue();
      if (v == null) continue;
      if (v instanceof Map) {
        for (Map.Entry<?, ?> s : ((Map<?, ?>) v).entrySet()) {
          if (s.getValue() != null) out.add(enc(e.getKey() + "[" + s.getKey() + "]") + "=" + enc(String.valueOf(s.getValue())));
        }
      } else if (v instanceof List) {
        for (Object x : (List<?>) v) out.add(enc(e.getKey()) + "=" + enc(String.valueOf(x)));
      } else {
        out.add(enc(e.getKey()) + "=" + enc(String.valueOf(v)));
      }
    }
    String s = out.toString();
    return s.isEmpty() ? "" : "?" + s;
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static Double retryAfter(Map<String, List<String>> headers) {
    List<String> v = headers.get("retry-after");
    if (v == null || v.isEmpty()) return null;
    try {
      double s = Double.parseDouble(v.get(0));
      return s >= 0 && s <= 60 ? s : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static double backoff(int attempt) {
    double base = Math.min(0.5 * Math.pow(2, attempt), 8.0);
    return base / 2 + ThreadLocalRandom.current().nextDouble() * base / 2;
  }

  /** The API key goes in every request, so the base URL must be HTTPS; plain HTTP only to this machine. */
  private static String checkBaseUrl(String baseUrl) {
    URI uri;
    try {
      uri = new URI(baseUrl);
    } catch (URISyntaxException e) {
      throw new YeslyException("The base URL is not a valid URL.");
    }
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if ("https".equalsIgnoreCase(scheme) && host != null && !host.isEmpty()) return baseUrl;
    if ("http".equalsIgnoreCase(scheme) && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host))) return baseUrl;
    throw new YeslyException("The base URL must use https:// (http:// is only allowed for localhost).");
  }

  private static String env(String name) {
    String v = System.getenv(name);
    return v == null || v.isEmpty() ? null : v;
  }
}
