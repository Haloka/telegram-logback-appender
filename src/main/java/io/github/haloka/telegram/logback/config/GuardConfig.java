package io.github.haloka.telegram.logback.config;


import lombok.Data;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

@Data
public class GuardConfig {
    private List<GuardStage> stages = new ArrayList<>();
    private String windowSize = "PT1M";

    public Duration getWindow() {
        return Duration.parse(windowSize);
    }
}