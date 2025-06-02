package io.github.haloka.telegram.logback;

import io.github.haloka.telegram.logback.config.GuardConfig;
import io.github.haloka.telegram.logback.config.GuardStage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Data;

class RateGuard {
    private final List<GuardStage> guardStages;
    private final Duration windowTime;
    private final int windowSlotSize;
    private final String timezone;
    private final ConcurrentHashMap<String, GuardState> alertStates = new ConcurrentHashMap<>();

    @Data
    private static class GuardState {
        private final String alertType;
        private final Window eventWindow;
        private long suppressionStartTime = 0;
        private Duration suppressionDuration = Duration.ZERO;
        private boolean suppressed = false;
        private long lastAlertTime = 0;
        private int alertCount = 0;

        public GuardState(String alertType, Duration windowTime, int windowSlotSize) {
            this.alertType = alertType;
            this.eventWindow = new Window(windowTime, windowSlotSize);
        }

        public void startSuppression(long timestamp, Duration duration) {
            suppressed = true;
            suppressionStartTime = timestamp;
            suppressionDuration = duration;
        }

        public void recordAlert(long timestamp) {
            if (timestamp - lastAlertTime > 0) {
                alertCount = 1;
            } else {
                alertCount++;
            }
            lastAlertTime = timestamp;
        }
    }


    public RateGuard(GuardConfig config, String timezone) {
        this.timezone = timezone;
        this.guardStages = config.getStages();
        this.windowTime = config.getWindow();
        this.windowSlotSize = this.guardStages.stream()
            .map(GuardStage::getCount)
            .reduce(Math::max)
            .orElse(0) + 1;
    }

    public SendDecision shouldSendAlert(long timestamp, String alertType) {
        GuardState state = alertStates.computeIfAbsent(alertType,
            key -> new GuardState(alertType, windowTime, windowSlotSize));

        state.getEventWindow().recordEvent(timestamp);

        if (state.isSuppressed()) {
            int currentCount = state.getEventWindow().getEventCount(timestamp);
            GuardStage nextStage = findSuppressionStage(currentCount);

            if (nextStage != null
                && nextStage.getDuration().compareTo(state.getSuppressionDuration()) > 0) {

                state.startSuppression(timestamp, nextStage.getDuration());
                return new SendDecision(true, true,
                    generateEscalationMessage(alertType, state, currentCount, nextStage));
            }

            return new SendDecision(false, false, null);
        }

        int currentCount = state.getEventWindow().getEventCount(timestamp);
        GuardStage stage = findSuppressionStage(currentCount);
        if (stage != null) {
            state.startSuppression(timestamp, stage.getDuration());
            return new SendDecision(true, true,
                generateSuppressionMessage(alertType, state, currentCount, stage));
        }

        state.recordAlert(timestamp);
        return new SendDecision(true, false, null);
    }

    private GuardStage findSuppressionStage(int count) {
        return guardStages.stream()
            .filter(stage -> count >= stage.getCount())
            .reduce((first, second) -> second)
            .orElse(null);
    }

    private String generateSuppressionMessage(String alertType, GuardState state, int currentCount, GuardStage stage) {
        Instant time =  Instant.ofEpochMilli(state.getSuppressionStartTime() + state.getSuppressionDuration().toMillis());

        return String.format("""
            <b>Alert Detention Notice</b>
    
            We've detected an alert going wild with notifications. If this continues, we'll have to take drastic measures:
            • Troublemaker: %s
            • Spam Rate: %d/minute
            • Timeout Duration: %d minutes
            • Release Time: %s
            • Power Level: %s
            
            """,
            alertType,
            currentCount,
            state.getSuppressionDuration().toMinutes(),
            Utils.formatDateTime(time, timezone),
            stage.getLabel()
        );
    }

    private String generateEscalationMessage(String alertType, GuardState state, int currentCount, GuardStage stage) {
        Instant time =  Instant.ofEpochMilli(state.getSuppressionStartTime() + state.getSuppressionDuration().toMillis());

        return String.format("""
            <b>Alert Suppression Escalation</b>
    
            This alert has gone completely out of control. We're forced to take more severe measures:
            • Perpetrator: %s
            • Spam Frequency: %d/minute
            • New Detention Period: %d minutes
            • Expected Parole Time: %s
            • Battle Level: %s
            
            """,
            alertType,
            currentCount,
            state.getSuppressionDuration().toMinutes(),
            Utils.formatDateTime(time, timezone),
            stage.getLabel()
        );
    }
}