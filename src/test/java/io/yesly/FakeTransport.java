package io.yesly;

import io.yesly.http.HttpTransport;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** Answers from a queue and records each request. */
final class FakeTransport implements HttpTransport {
  static final class Call {
    final String method;
    final String url;
    final Map<String, String> headers;
    final String body;

    Call(String method, String url, Map<String, String> headers, String body) {
      this.method = method;
      this.url = url;
      this.headers = headers;
      this.body = body;
    }
  }

  final List<Call> calls = new ArrayList<>();
  private final Deque<Object> answers = new ArrayDeque<>();

  FakeTransport answer(int status, String body) {
    return answer(status, body, Map.of());
  }

  FakeTransport answer(int status, String body, Map<String, String> headers) {
    Map<String, List<String>> h = new java.util.TreeMap<>();
    headers.forEach((k, v) -> h.put(k, List.of(v)));
    answers.add(new Response(status, h, body));
    return this;
  }

  FakeTransport fail(IOException e) {
    answers.add(e);
    return this;
  }

  @Override
  public Response send(String method, String url, Map<String, String> headers, String body, Duration timeout) throws IOException {
    calls.add(new Call(method, url, Map.copyOf(headers), body));
    Object a = answers.poll();
    if (a == null) throw new IllegalStateException("Unexpected request " + method + " " + url);
    if (a instanceof IOException) throw (IOException) a;
    return (Response) a;
  }
}
