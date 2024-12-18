package io.github.haloka.telegram.logback.config;

import java.time.Duration;
import lombok.Data;

@Data
public class ThreadConfig {
    private int corePoolSize = 2;
    private int maxPoolSize = 2;
    private int queueCapacity = 500;
    private String keepAliveTime = "PT1M";

    public Duration getKeepAliveTimeAsJava() {
        return Duration.parse(keepAliveTime);
    }
}