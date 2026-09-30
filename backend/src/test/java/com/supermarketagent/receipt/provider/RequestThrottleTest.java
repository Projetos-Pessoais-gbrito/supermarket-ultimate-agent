package com.supermarketagent.receipt.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RequestThrottleTest {

    @Test
    void firstRequestIsImmediate() throws InterruptedException {
        RequestThrottle throttle = new RequestThrottle(Duration.ofSeconds(10));

        long start = System.nanoTime();
        throttle.acquire();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void spacesConsecutiveRequestsByTheMinimumInterval() throws InterruptedException {
        RequestThrottle throttle = new RequestThrottle(Duration.ofMillis(50));

        long start = System.nanoTime();
        throttle.acquire();
        throttle.acquire();
        throttle.acquire();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(100));
    }
}
