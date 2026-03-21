# Rate Limiter

A showcase of what happens when you ask [Claude Code](https://claude.ai/claude-code) a single question:

> *"Create a sliding window rate limiter, no GC, O(1) implementation"*

**Model:** Claude Sonnet 4.6 (`claude-sonnet-4-6`)
**Tool:** Claude Code CLI

---

## What Claude did

Starting from a blank Gradle project, Claude autonomously:

1. **Asked clarifying questions** — algorithm variant, GC scope, concurrency, API shape, Java version, clock injection
2. **Wrote a full spec** — `docs/specs/sliding-window-log-rate-limiter.md` covering requirements, design, edge cases, and acceptance criteria
3. **Implemented the class** — zero per-request allocation, O(1) ring buffer, Guava preconditions
4. **Wrote 17 tests** — JUnit 5 + AssertJ, injectable clock, no `Thread.sleep`, parameterized edge cases

All in one session, with no manual code written.

---

## The implementation

### Algorithm

A pre-allocated `long[]` ring buffer of size `limit` stores request timestamps. On each `tryAcquire()`:

- **Buffer not full** → record timestamp, return `true`
- **Buffer full, oldest slot expired** (`now - oldest >= windowDurationMs`) → overwrite slot, advance head, return `true`
- **Buffer full, oldest still in window** → return `false`

One array read, one optional array write. No objects created. No GC pressure.

```
long[] timestamps   ← circular buffer, size = limit
int    head         ← index of the oldest slot
int    count        ← slots filled (0 → limit, then stays)
```

### API

```java
// Production
var limiter = new SlidingWindowRateLimiter(100, 1_000L, System::currentTimeMillis);

// Test (injectable clock)
var clock = new AtomicLong(0L);
var limiter = new SlidingWindowRateLimiter(100, 1_000L, clock::get);

boolean allowed = limiter.tryAcquire(); // true if within limit
```

---

## Project structure

```
src/
  main/java/dev/knyuen/ratelimiter/
    SlidingWindowRateLimiter.java     ← implementation
  test/java/dev/knyuen/ratelimiter/
    SlidingWindowRateLimiterTest.java ← 17 tests
docs/
  specs/sliding-window-log-rate-limiter.md  ← full spec
  prompts.md                                ← session prompt log
```

---

## Run the tests

```bash
./gradlew test
```

---

## The Claude session

The full prompt/response log is in [`docs/prompts.md`](docs/prompts.md). The session used three Claude Code skills:

| Skill | What it did |
|---|---|
| `/write-spec` | Clarified requirements, wrote the spec |
| `/write-java-code` | Implemented `SlidingWindowRateLimiter.java` |
| `/write-java-test` | Wrote the full JUnit 5 test suite |

A custom hook (`record-prompts`) was also set up during the session to automatically log every prompt and response to `docs/prompts.md`.
