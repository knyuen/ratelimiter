package dev.knyuen.ratelimiter;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.LongSupplier;

/**
 * A single-key, single-threaded sliding window log rate limiter.
 *
 * <p>Enforces an exact request limit over a rolling time window. Uses a pre-allocated circular
 * buffer of {@code long} timestamps sized to {@code limit}, yielding O(1) time and zero
 * per-request heap allocation after construction.
 *
 * <p>This class is not thread-safe.
 */
public final class SlidingWindowRateLimiter {

    private final long[] timestamps;
    private final long windowDurationMs;
    private final LongSupplier clock;
    private final int limit;

    private int head;
    private int count;

    /**
     * @param limit            maximum number of requests allowed per window; must be &gt;= 1
     * @param windowDurationMs rolling window length in milliseconds; must be &gt;= 1
     * @param clock            time source in milliseconds; use {@code System::currentTimeMillis}
     *                         for production
     * @throws IllegalArgumentException if {@code limit < 1} or {@code windowDurationMs < 1}
     * @throws NullPointerException     if {@code clock} is null
     */
    public SlidingWindowRateLimiter(int limit, long windowDurationMs, LongSupplier clock) {
        checkArgument(limit >= 1, "Missing valid limit (must be >= 1)");
        checkArgument(windowDurationMs >= 1, "Missing valid windowDurationMs (must be >= 1)");
        checkNotNull(clock, "Missing clock");

        this.limit = limit;
        this.windowDurationMs = windowDurationMs;
        this.clock = clock;
        this.timestamps = new long[limit];
        this.head = 0;
        this.count = 0;
    }

    /**
     * Attempts to acquire a permit.
     *
     * <p>Records the current timestamp and returns {@code 0} if fewer than {@code limit} requests
     * have occurred within the rolling window {@code (now - windowDurationMs, now]}. Returns a
     * positive value representing the number of milliseconds the caller must wait before the next
     * permit becomes available (retry-after). Makes no heap allocations.
     *
     * @return {@code 0} if the request is permitted; a positive number of milliseconds to wait
     *     before retrying otherwise
     */
    public long tryAcquire() {
        long now = clock.getAsLong();

        if (count < limit) {
            timestamps[(head + count) % limit] = now;
            count++;
            return 0L;
        }

        long oldest = timestamps[head];
        if (now - oldest >= windowDurationMs) {
            timestamps[head] = now;
            head = (head + 1) % limit;
            return 0L;
        }

        return windowDurationMs - (now - oldest);
    }
}
