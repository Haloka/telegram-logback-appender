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

    public Duration getDuration() {
        return duration;
    }

    public static void main(String[] args) {
        GuardStage config = new GuardStage();
        config.setCount(2);
        config.setDuration("PT3M");
        config.setLabel("lv1");

        System.out.println(Duration.parse("PT3M"));
    }
}
