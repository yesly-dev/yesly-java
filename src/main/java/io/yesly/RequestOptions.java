package io.yesly;

import java.time.Duration;

/** Options for one request. */
public final class RequestOptions {
  final String idempotencyKey;
  final Duration timeout;
  final Integer maxRetries;

  private RequestOptions(Builder b) {
    this.idempotencyKey = b.idempotencyKey;
    this.timeout = b.timeout;
    this.maxRetries = b.maxRetries;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String idempotencyKey;
    private Duration timeout;
    private Integer maxRetries;

    private Builder() {}

    /** For POSTs that take one. Generated once per call when not given, and reused on retries. */
    public Builder idempotencyKey(String idempotencyKey) {
      this.idempotencyKey = idempotencyKey;
      return this;
    }

    public Builder timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    public Builder maxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    public RequestOptions build() {
      return new RequestOptions(this);
    }
  }
}
