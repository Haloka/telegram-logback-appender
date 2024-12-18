package io.github.haloka.telegram.logback;

import ch.qos.logback.classic.spi.IThrowableProxy;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.experimental.UtilityClass;

@UtilityClass
class Utils {

    int GROUP_MESSAGE_LENGTH = 60;
    int GROUP_THROWABLE_LENGTH = 250;

    static String formatDateTime(Instant instant, String timezone) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of(timezone))
            .format(instant);
    }

    String generateGroupKey(String message, IThrowableProxy throwable) {
        StringBuilder key = new StringBuilder();
        if (throwable != null) {
            key.append(throwable.getClassName()).append(":");
        }

        key.append(getSubMessage(message, GROUP_MESSAGE_LENGTH));
        if (throwable != null) {
            key.append(":").append(getSubMessage(throwable.getMessage(), GROUP_THROWABLE_LENGTH));
        }
        return key.toString();
    }

    String getSubMessage(String message, int maxLength) {
        return message.length() > maxLength ? message.substring(0, maxLength) + "..." : message;
    }

    static boolean hasText(String str) {
        return str != null && !str.isBlank();
    }

    static String htmlEscape(String input) {
        if (input == null) {
            return null;
        }

        StringBuilder escaped = new StringBuilder(input.length() * 6 / 5);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '&':
                    escaped.append("&amp;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&#39;");
                    break;
                default:
                    escaped.append(c);
            }
        }

        return escaped.toString();

    }
}
