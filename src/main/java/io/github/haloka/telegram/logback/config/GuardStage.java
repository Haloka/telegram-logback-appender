package io.github.haloka.telegram.logback.config;

import java.time.Duration;
import lombok.Data;

@Data
public class GuardStage {
    private int count;
    /** ISO-8601 duration format: PT1H30M */
    private Duration duration;
    private String label;

    public void setDuration(String value) {
        this.duration = Duration.parse(value);
    }
}
