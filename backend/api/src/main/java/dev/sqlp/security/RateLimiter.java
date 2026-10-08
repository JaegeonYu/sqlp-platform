package dev.sqlp.security;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.sqlp.common.RateLimitedException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import org.springframework.stereotype.Component;

/**
 * 키(IP, 이메일 등)별 토큰 버킷. 단일 인스턴스 운영을 전제로 메모리에 둔다.
 * 오래 쓰지 않은 키는 캐시에서 빠지므로 메모리가 무한히 늘지 않는다.
 */
@Component
public class RateLimiter {

	public enum Plan {

		/** IP별 인증 요청(로그인·가입): 분당 20회 */
		AUTH_PER_IP(20, Duration.ofMinutes(1)),

		/** 이메일별 로그인 시도: 15분에 10회 */
		LOGIN_PER_EMAIL(10, Duration.ofMinutes(15)),

		/** 사용자별 초대 링크 확인·수락: 10분에 30회 */
		INVITE_PER_USER(30, Duration.ofMinutes(10));

		private final long capacity;

		private final Duration period;

		Plan(long capacity, Duration period) {
			this.capacity = capacity;
			this.period = period;
		}

	}

	private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
		.maximumSize(100_000)
		.expireAfterAccess(Duration.ofHours(1))
		.build();

	public void consume(Plan plan, String key) {
		Bucket bucket = this.buckets.get(plan.name() + "|" + key, (ignored) -> newBucket(plan));
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			throw new RateLimitedException(TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
		}
	}

	/** 테스트 격리용 */
	public void reset() {
		this.buckets.invalidateAll();
	}

	private static Bucket newBucket(Plan plan) {
		Bandwidth limit = Bandwidth.builder().capacity(plan.capacity).refillGreedy(plan.capacity, plan.period).build();
		return Bucket.builder().addLimit(limit).build();
	}

}
