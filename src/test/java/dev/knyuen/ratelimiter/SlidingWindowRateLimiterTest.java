package dev.knyuen.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SlidingWindowRateLimiterTest {

    private static final int DEFAULT_LIMIT = 3;
    private static final long DEFAULT_WINDOW_MS = 1_000L;

    private AtomicLong clock;
    private SlidingWindowRateLimiter rateLimiter;

    @BeforeEach
    void beforeEach() {
        clock = new AtomicLong(0L);
        rateLimiter = new SlidingWindowRateLimiter(DEFAULT_LIMIT, DEFAULT_WINDOW_MS, clock::get);
    }

    // -------------------------------------------------------------------------
    // Happy-path: permit acquisition
    // -------------------------------------------------------------------------

    @Test
    void WHEN_requests_within_limit_THEN_all_tryAcquire_return_zero() {
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            assertThat(rateLimiter.tryAcquire()).isEqualTo(0L);
        }
    }

    @Test
    void WHEN_requests_exceed_limit_within_window_THEN_tryAcquire_returns_retry_after() {
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            rateLimiter.tryAcquire();
        }
        // oldest = 0, now = 0, retry-after = windowDurationMs - 0 = 1000
        assertThat(rateLimiter.tryAcquire()).isEqualTo(DEFAULT_WINDOW_MS);
    }

    // -------------------------------------------------------------------------
    // Window boundary
    // -------------------------------------------------------------------------

    @Test
    void WHEN_oldest_request_exactly_at_window_boundary_THEN_tryAcquire_returns_zero() {
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            rateLimiter.tryAcquire();
        }
        clock.set(DEFAULT_WINDOW_MS);
        assertThat(rateLimiter.tryAcquire()).isEqualTo(0L);
    }

    @Test
    void WHEN_oldest_request_just_inside_window_THEN_tryAcquire_returns_one_ms_retry_after() {
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            rateLimiter.tryAcquire();
        }
        clock.set(DEFAULT_WINDOW_MS - 1);
        // oldest = 0, now = 999, retry-after = 1000 - 999 = 1
        assertThat(rateLimiter.tryAcquire()).isEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // Sliding behaviour: old slots reclaimed as time advances
    // -------------------------------------------------------------------------

    @Test
    void WHEN_window_expires_THEN_new_requests_are_allowed_again() {
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            rateLimiter.tryAcquire();
        }
        assertThat(rateLimiter.tryAcquire()).isGreaterThan(0L);

        clock.set(DEFAULT_WINDOW_MS);
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            assertThat(rateLimiter.tryAcquire()).isEqualTo(0L);
        }
        assertThat(rateLimiter.tryAcquire()).isGreaterThan(0L);
    }

    // -------------------------------------------------------------------------
    // limit = 1 edge case
    // -------------------------------------------------------------------------

    @Test
    void GIVEN_limit_1_WHEN_rapid_calls_THEN_only_first_returns_zero() {
        var limiter = new SlidingWindowRateLimiter(1, DEFAULT_WINDOW_MS, clock::get);

        assertThat(limiter.tryAcquire()).isEqualTo(0L);
        assertThat(limiter.tryAcquire()).isGreaterThan(0L);
        assertThat(limiter.tryAcquire()).isGreaterThan(0L);
    }

    @Test
    void GIVEN_limit_1_WHEN_window_expires_THEN_next_call_returns_zero() {
        var limiter = new SlidingWindowRateLimiter(1, DEFAULT_WINDOW_MS, clock::get);

        limiter.tryAcquire();
        clock.set(DEFAULT_WINDOW_MS);
        assertThat(limiter.tryAcquire()).isEqualTo(0L);
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Nested
    class GIVEN_invalid_limit_WHEN_constructed_THEN_throws_class {

        @ParameterizedTest
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        void GIVEN_invalid_limit_WHEN_constructed_THEN_throws(int invalidLimit) {
            assertThatThrownBy(
                    () -> new SlidingWindowRateLimiter(invalidLimit, DEFAULT_WINDOW_MS, clock::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing valid limit");
        }
    }

    @Nested
    class GIVEN_invalid_window_WHEN_constructed_THEN_throws_class {

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
        void GIVEN_invalid_window_WHEN_constructed_THEN_throws(long invalidWindow) {
            assertThatThrownBy(
                    () -> new SlidingWindowRateLimiter(DEFAULT_LIMIT, invalidWindow, clock::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing valid windowDurationMs");
        }
    }

    @Test
    void WHEN_clock_is_null_THEN_constructor_throws() {
        assertThatThrownBy(() -> new SlidingWindowRateLimiter(DEFAULT_LIMIT, DEFAULT_WINDOW_MS, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Missing clock");
    }

    // -------------------------------------------------------------------------
    // Retry-after precision
    // -------------------------------------------------------------------------

    @Nested
    class WHEN_denied_THEN_retry_after_equals_remaining_window_time_class {

        static Stream<org.junit.jupiter.params.provider.Arguments>
                WHEN_denied_THEN_retry_after_equals_remaining_window_time() {
            // (clockMs, expectedRetryAfterMs) — buffer pre-filled at t=0, limit=3, window=1000ms
            return Stream.of(
                arguments(0L,   1_000L),   // now == oldest → full window remaining
                arguments(499L,   501L),   // 499ms elapsed → 501ms remaining
                arguments(999L,     1L)    // 1ms short of expiry
            );
        }

        @ParameterizedTest
        @MethodSource
        void WHEN_denied_THEN_retry_after_equals_remaining_window_time(
                long clockMs, long expectedRetryAfterMs) {
            for (int i = 0; i < DEFAULT_LIMIT; i++) {
                rateLimiter.tryAcquire();
            }
            clock.set(clockMs);
            assertThat(rateLimiter.tryAcquire()).isEqualTo(expectedRetryAfterMs);
        }
    }

    // -------------------------------------------------------------------------
    // Interleaved timing: mixed expiry across multiple slots
    // -------------------------------------------------------------------------

    @Nested
    class WHEN_requests_arrive_at_different_times_THEN_only_in_window_requests_count_class {

        static Stream<org.junit.jupiter.params.provider.Arguments>
                WHEN_requests_arrive_at_different_times_THEN_only_in_window_requests_count() {
            // (t0RequestCount, t1Ms, t1RequestCount, t2Ms, expectedResult)
            // limit=2, window=1000ms; 0L = permitted, >0 = retry-after ms
            return Stream.of(
                // 1 req at t=0, 1 at t=500, query at t=1001: oldest(t=0) expired → permitted
                arguments(1, 500L, 1, 1001L, 0L),
                // 2 reqs at t=0, query at t=999: oldest(t=0) still active → retry-after=1ms
                arguments(2, 0L,   0,  999L, 1L),
                // 2 reqs at t=0, query at t=1000: oldest(t=0) exactly at boundary → permitted
                arguments(2, 0L,   0, 1000L, 0L)
            );
        }

        @ParameterizedTest
        @MethodSource
        void WHEN_requests_arrive_at_different_times_THEN_only_in_window_requests_count(
                int t0Count, long t1Ms, int t1Count, long t2Ms, long expected) {
            var limiter = new SlidingWindowRateLimiter(2, DEFAULT_WINDOW_MS, clock::get);

            for (int i = 0; i < t0Count; i++) {
                limiter.tryAcquire();
            }
            clock.set(t1Ms);
            for (int i = 0; i < t1Count; i++) {
                limiter.tryAcquire();
            }
            clock.set(t2Ms);
            assertThat(limiter.tryAcquire()).isEqualTo(expected);
        }
    }
}
