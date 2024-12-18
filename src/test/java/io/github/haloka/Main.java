package io.github.haloka;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {

    public static void main(String[] args) throws InterruptedException {
        log.error("Hello world!" + "--=");
        System.out.println("Hello world!");

        Thread.sleep(1000 * 1);

        log.error("Hello world!" + "--=");
        log.error("Hello world!" + "--=");

        Thread.sleep(1000 * 1);
        log.error("Hello world!" + "--=");
        log.error("Hello world!" + "--=");
        log.error("Hello world!" + "--=");
        log.error("Hello world!" + "--=");
        log.error("Hello world!" + "--=");
        Thread.sleep(1000 * 1);
    }


}