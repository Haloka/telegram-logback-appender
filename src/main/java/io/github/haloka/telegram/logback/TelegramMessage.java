package io.github.haloka.telegram.logback;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
class TelegramMessage {
    private String chatId;
    private String text;
    private String parseMode;

    public TelegramMessage(String chatId, String text) {
        this.chatId = chatId;
        this.text = text;
        this.parseMode = "HTML";
    }
}
