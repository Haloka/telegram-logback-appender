package io.github.haloka;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestError {

    public static void main(String[] args) {
        log.error("Test error message with exception", new RuntimeException("This is a test exception"));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            log.error("Sleep interrupted", e);
        }
        log.error("Test error message with exception", new RuntimeException("This is a test exception"));
        log.error("Test error message with exception", new RuntimeException("This is a test exception"));
        log.error("Test error message with exception", new RuntimeException("This is a test exception"));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            log.error("Sleep interrupted", e);
        }
        log.error("Test error message with exception", new RuntimeException("This is a test exception"));
        log.error("Test error message with exception", new RuntimeException("This is a test exception"));
        log.error("Test error message with exception", new RuntimeException("This is a test exception"));
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("Sleep interrupted", e);
            }
        }
    }
}
