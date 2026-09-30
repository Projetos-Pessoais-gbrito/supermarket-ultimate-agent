package com.supermarketagent.receipt.provider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Guarantees a minimum interval between consecutive requests to a SEFAZ server. */
public final class RequestThrottle {

    private final long minIntervalNanos;
    private long nextAllowedAt;

    public RequestThrottle(Duration minInterval) {
        this.minIntervalNanos = minInterval.toNanos();
        this.nextAllowedAt = System.nanoTime();
    }

    /** Blocks until the next request is allowed. */
    public synchronized void acquire() throws InterruptedException {
        long now = System.nanoTime();
        long wait = nextAllowedAt - now;
        if (wait > 0) {
            TimeUnit.NANOSECONDS.sleep(wait);
            now = System.nanoTime();
        }
        nextAllowedAt = now + minIntervalNanos;
    }
}
