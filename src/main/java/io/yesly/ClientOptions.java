package io.yesly;

import io.yesly.http.HttpTransport;
import java.time.Duration;

/** How a client connects. Every value has a default. */
public final class ClientOptions {
  final String apiKey;
  final String baseUrl;
  final String apiVersion;
  final Duration timeout;
  final int maxRetries;
  final HttpTransport transport;

  private ClientOptions(Builder b) {
    this.apiKey = b.apiKey;
    this.baseUrl = b.baseUrl;
    this.apiVersion = b.apiVersion;
    this.timeout = b.timeout;
    this.maxRetries = b.maxRetries;
    this.transport = b.transport;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String apiKey;
    private String baseUrl;
    private String apiVersion;
    private Duration timeout = Duration.ofSeconds(30);
    private int maxRetries = 2;
    private HttpTransport transport;

    private Builder() {}

    /** Defaults to the {@code YESLY_API_KEY} environment variable. */
    public Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    /** Defaults to {@code https://api.yesly.dev/v1}, or {@code YESLY_BASE_URL} when set. Must be HTTPS, except for localhost. */
    public Builder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    /** {@code Yesly-Version}. Defaults to the version this library was generated for. */
    public Builder apiVersion(String apiVersion) {
      this.apiVersion = apiVersion;
      return this;
    }

    /** Per attempt. Default 30 seconds. */
    public Builder timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    /** Retries after a network error, 408, 429 or 5xx. Default 2. */
    public Builder maxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    public Builder transport(HttpTransport transport) {
      this.transport = transport;
      return this;
    }

    public ClientOptions build() {
      return new ClientOptions(this);
    }
  }
}
