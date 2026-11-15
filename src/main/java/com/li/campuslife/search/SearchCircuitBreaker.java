package com.li.campuslife.search;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简易熔断：连续失败 3 次后打开 30 秒，期间检索直接走 DB；30 秒后自动放行试探。
 */
@Component
public class SearchCircuitBreaker {

    private static final int FAILURE_THRESHOLD = 3;
    private static final long OPEN_MILLIS = 30_000L;

    private final AtomicInteger failures = new AtomicInteger();
    private volatile long openUntil = 0L;

    public boolean allowRequest() {
        return System.currentTimeMillis() >= openUntil;
    }

    public void recordSuccess() {
        failures.set(0);
        openUntil = 0L;
    }

    public void recordFailure() {
        if (failures.incrementAndGet() >= FAILURE_THRESHOLD) {
            openUntil = System.currentTimeMillis() + OPEN_MILLIS;
            failures.set(0);
        }
    }
}
