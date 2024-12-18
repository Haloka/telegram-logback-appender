package io.github.haloka.telegram.logback.config;

import java.time.Duration;
import lombok.Data;

@Data
public class RateConfig {
    private int maxMessagesPerMinute = 20;
    private String window = "PT1M";

    public Duration getWindowAsJava() {
        return Duration.parse(window);
    }
}