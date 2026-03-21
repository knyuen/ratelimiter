# Specification: Sliding Window Log Rate Limiter

**Issue:** N/A (greenfield)
**Status:** Draft
**Author:**
**Date:** 2026-03-21

---

## Overview

A single-key, single-threaded sliding window log rate limiter implemented in Java with O(1) time complexity and zero per-request heap allocation. It enforces an exact request limit over a rolling time window using a pre-allocated circular buffer of `long` timestamps, sized to the configured limit.

---

## Goals

- Enforce an exact request rate limit (no approximation)
- O(1) `tryAcquire()` — constant time regardless of limit size
- Zero heap allocation after construction — no GC pressure on the hot path
- Injectable clock for deterministic testing
- Simple, minimal API surface

## Non-Goals

- Multi-key (per-user, per-IP) rate limiting
- Thread safety / concurrent access
- Distributed coordination
- Persistence or state recovery across JVM restarts
- Metrics, observability hooks, or retry-after metadata
- Dynamic reconfiguration after construction

---

## Background & Context

The canonical sliding window log algorithm tracks exact request timestamps in a queue and evicts entries older than the window on each call. This gives precise enforcement but typically incurs per-request allocation (queue nodes) and O(n) eviction sweeps.

This implementation eliminates both costs by exploiting a key invariant: **a ring buffer of size `limit` is sufficient to store all in-flight timestamps**. If the buffer is full, only the oldest slot matters — if it falls outside the window the request is allowed (overwriting that slot); if it is still inside the window the limit is exceeded and the request is denied. This reduces every operation to a single array read and optional write.

---

## Requirements

### Functional Requirements

| ID   | Requirement                                                                                                                            | Priority |
|------|----------------------------------------------------------------------------------------------------------------------------------------|----------|
| FR-1 | Accept `limit` (max requests) and `windowDurationMs` (window length in milliseconds) as constructor parameters                          | Must     |
| FR-2 | Accept a `LongSupplier` clock as a constructor parameter; use it as the sole time source                                               | Must     |
| FR-3 | Expose a `boolean tryAcquire()` method that returns `true` if the request is within the limit, `false` otherwise                      | Must     |
| FR-4 | When `tryAcquire()` returns `true`, record the current timestamp so it counts against the window                                       | Must     |
| FR-5 | Enforce the limit exactly — no approximations; a request at time T is allowed only if fewer than `limit` requests occurred in `(T - windowDurationMs, T]` | Must |
| FR-6 | Allocate all internal state (timestamp buffer) at construction time; make zero heap allocations per `tryAcquire()` call                | Must     |
| FR-7 | Operate correctly as a single-threaded component; no synchronization guarantees required or provided                                   | Must     |
| FR-8 | Reject `limit < 1` or `windowDurationMs < 1` with `IllegalArgumentException` at construction                                          | Must     |

### Non-Functional Requirements

| ID    | Requirement                                                                    |
|-------|--------------------------------------------------------------------------------|
| NFR-1 | `tryAcquire()` executes in O(1) time — one array index read, one optional write |
| NFR-2 | Memory footprint after construction: `O(limit)` — a single `long[limit]` array |
| NFR-3 | Target Java 21+; use no external dependencies                                  |
| NFR-4 | Class is package-private or public; no framework coupling                      |

---

## Design

### Data Structure

```
long[] timestamps  // circular buffer, length = limit
int    head        // index of the oldest slot (next candidate to overwrite)
int    count       // number of slots filled (0..limit); saturates at limit
```

All fields are primitives or a primitive array — no object allocation after construction.

### Algorithm — `tryAcquire()`

```
now = clock.getAsLong()

if count < limit:
    // Buffer not yet full — always allow
    timestamps[head + count % limit] = now
    count++
    return true

oldest = timestamps[head]
if now - oldest >= windowDurationMs:
    // Oldest request has expired — reclaim its slot
    timestamps[head] = now
    head = (head + 1) % limit
    return true

return false   // limit exceeded, oldest still within window
```

*The slot at `head` is always the oldest recorded timestamp when the buffer is full. Checking only that one slot is sufficient because if the oldest is still within the window, all others are too.*

### Invariants

- `timestamps[head]` is always the oldest recorded timestamp (when `count == limit`)
- The buffer wraps via `head = (head + 1) % limit` after each accepted request that overwrites an expired slot
- `count` transitions to `limit` and stays there once the buffer is full

---

## API

```java
package dev.knyuen.ratelimiter;

import java.util.function.LongSupplier;

public final class SlidingWindowRateLimiter {

    /**
     * @param limit            maximum number of requests allowed per window
     * @param windowDurationMs window length in milliseconds (exclusive lower bound)
     * @param clock            time source; {@code System::currentTimeMillis} for production
     * @throws IllegalArgumentException if limit < 1 or windowDurationMs < 1
     */
    public SlidingWindowRateLimiter(int limit, long windowDurationMs, LongSupplier clock) { ... }

    /**
     * Attempts to acquire a permit.
     *
     * @return {@code true} if the request is within the rate limit, {@code false} otherwise
     */
    public boolean tryAcquire() { ... }
}
```

---

## User Stories / Scenarios

**As a** caller, **I want to** invoke `tryAcquire()` and receive `true` when under the limit, **so that** I can proceed with the operation.

**As a** caller, **I want to** receive `false` immediately when the limit is exceeded, **so that** I can back off without blocking.

**As a** test author, **I want to** inject a controlled clock, **so that** I can write deterministic, time-independent tests without `Thread.sleep`.

**Edge cases:**

| Scenario | Expected behaviour |
|---|---|
| First `limit` calls within window | All return `true` |
| Call `limit + 1` within window | Returns `false` |
| Call made exactly `windowDurationMs` ms after the oldest recorded request | Returns `true` (boundary is inclusive on the right: `now - oldest >= windowDurationMs`) |
| Call made `windowDurationMs - 1` ms after oldest | Returns `false` |
| Clock goes backwards (monotonicity not guaranteed) | Behaviour undefined; caller is responsible for providing a monotonic clock if required |
| `limit = 1`, rapid successive calls | First returns `true`, subsequent return `false` until window expires |

---

## Acceptance Criteria

- [ ] `SlidingWindowRateLimiter(limit, windowDurationMs, clock)` compiles and constructs without error for valid inputs
- [ ] Constructor throws `IllegalArgumentException` for `limit < 1`
- [ ] Constructor throws `IllegalArgumentException` for `windowDurationMs < 1`
- [ ] Exactly `limit` calls within a window return `true`; the `limit + 1`th call returns `false`
- [ ] After the oldest request expires (`now - oldest >= windowDurationMs`), a new call returns `true`
- [ ] No `new` expressions, boxing, or collection usage inside `tryAcquire()` (verified by code review)
- [ ] All tests use an injected clock; no `Thread.sleep` in the test suite
- [ ] `tryAcquire()` performs exactly one array read and at most one array write per invocation

---

## Open Questions

_None — all gaps resolved during spec review._
