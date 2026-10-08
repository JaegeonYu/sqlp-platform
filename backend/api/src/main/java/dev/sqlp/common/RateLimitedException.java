package dev.sqlp.common;

import org.springframework.http.HttpStatus;

public class RateLimitedException extends ApiException {

	private final long retryAfterSeconds;

	public RateLimitedException(long retryAfterSeconds) {
		super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청이 너무 많습니다. 잠시 후 다시 시도하세요.");
		this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
	}

	public long retryAfterSeconds() {
		return this.retryAfterSeconds;
	}

}
