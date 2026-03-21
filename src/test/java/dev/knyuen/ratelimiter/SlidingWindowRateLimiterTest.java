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
    void WHEN_requests_within_limit_THEN_all_tryAcquire_return_true() {
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            assertThat(rateLimiter.tryAcquire()).isTrue();
        }
    }

    @Test
    void WHEN_requests_exceed_limit_within_window_THEN_tryAcquire_returns_false() {
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            rateLimiter.tryAcquire();
        }
        assertThat(rateLimiter.tryAcquire()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Window boundary
    // -------------------------------------------------------------------------

    @Test
    void WHEN_oldest_request_exactly_at_window_boundary_THEN_tryAcquire_returns_true() {
        // Fill the buffer at t=0
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            rateLimiter.tryAcquire();
        }

        // Advance clock to exactly windowDurationMs — oldest slot now expires
        clock.set(DEFAULT_WINDOW_MS);
        assertThat(rateLimiter.tryAcquire()).isTrue();
    }

    @Test
    void WHEN_oldest_request_just_inside_window_THEN_tryAcquire_returns_false() {
        // Fill the buffer at t=0
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            rateLimiter.tryAcquire();
        }

        // Advance clock to one millisecond short of expiry
        clock.set(DEFAULT_WINDOW_MS - 1);
        assertThat(rateLimiter.tryAcquire()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Sliding behaviour: old slots reclaimed as time advances
    // -------------------------------------------------------------------------

    @Test
    void WHEN_window_expires_THEN_new_requests_are_allowed_again() {
        // Fill at t=0
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            rateLimiter.tryAcquire();
        }
        assertThat(rateLimiter.tryAcquire()).isFalse();

        // Advance past window — each expired slot frees one permit
        clock.set(DEFAULT_WINDOW_MS);
        for (int i = 0; i < DEFAULT_LIMIT; i++) {
            assertThat(rateLimiter.tryAcquire()).isTrue();
        }
        assertThat(rateLimiter.tryAcquire()).isFalse();
    }

    // -------------------------------------------------------------------------
    // limit = 1 edge case
    // -------------------------------------------------------------------------

    @Test
    void GIVEN_limit_1_WHEN_rapid_calls_THEN_only_first_returns_true() {
        var limiter = new SlidingWindowRateLimiter(1, DEFAULT_WINDOW_MS, clock::get);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void GIVEN_limit_1_WHEN_window_expires_THEN_next_call_returns_true() {
        var limiter = new SlidingWindowRateLimiter(1, DEFAULT_WINDOW_MS, clock::get);

        limiter.tryAcquire();
        clock.set(DEFAULT_WINDOW_MS);
        assertThat(limiter.tryAcquire()).isTrue();
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
    // Interleaved timing: mixed expiry across multiple slots
    // -------------------------------------------------------------------------

    @Nested
    class WHEN_requests_arrive_at_different_times_THEN_only_in_window_requests_count_class {

        static Stream<org.junit.jupiter.params.provider.Arguments>
                WHEN_requests_arrive_at_different_times_THEN_only_in_window_requests_count() {
            // (t0RequestCount, t1Ms, t1RequestCount, t2Ms, expectedAtT2)
            // limit=2, window=1000ms
            return Stream.of(
                // 1 request at t=0, 1 at t=500, query at t=1001: t=0 expired, t=500 still active → true
                arguments(1, 500L, 1, 1001L, true),
                // 2 requests at t=0, query at t=999: both still active → false
                arguments(2, 0L,   0, 999L,  false),
                // 2 requests at t=0, query at t=1000: oldest expired → true
                arguments(2, 0L,   0, 1000L, true)
            );
        }

        @ParameterizedTest
        @MethodSource
        void WHEN_requests_arrive_at_different_times_THEN_only_in_window_requests_count(
                int t0Count, long t1Ms, int t1Count, long t2Ms, boolean expected) {
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
