package io.github.haloka.telegram.logback;

import lombok.Data;

@Data
class SendDecision {
    private final boolean shouldSend;
    private final boolean isSuppressionNotification;
    private final String suppressionMessage;
}