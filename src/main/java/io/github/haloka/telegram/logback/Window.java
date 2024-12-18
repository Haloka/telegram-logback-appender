package io.github.haloka.telegram.logback;

import java.time.Duration;
import java.util.Arrays;

class Window {
    private final long[] timestamps;
    private final Duration windowTime;
    private int currentIndex = 0;

    public Window(Duration windowTime, int windowSlotSize) {
        this.windowTime = windowTime;
        this.timestamps = new long[windowSlotSize];
    }

    public void recordEvent(long timestamp) {
        timestamps[currentIndex] = timestamp;
        currentIndex = (currentIndex + 1) % timestamps.length;
    }

    public int getEventCount(long timestamp) {
        long windowStart = timestamp - windowTime.toMillis();
        return (int) Arrays.stream(timestamps)
                .filter(t -> t > windowStart)
                .count();
    }
}