package io.github.haloka.telegram.logback.config;

import java.time.Duration;
import lombok.Data;

@Data
public class HttpConfig {
    private String connectTimeout = "PT10S";
    private String readTimeout = "PT10S";
    private boolean followRedirects = true;

    public Duration getConnectTimeoutAsJava() {
        return Duration.parse(connectTimeout);
    }

    public Duration getReadTimeoutAsJava() {
        return Duration.parse(readTimeout);
    }
}