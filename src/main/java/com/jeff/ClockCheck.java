package com.jeff;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;


@ApplicationScoped
public class ClockCheck {
    private static final Logger log = LoggerFactory.getLogger(ClockCheck.class);


    @Scheduled(every = "1h")
    void check() {
        log.info("=== CLOCK CHECK ===");
        log.info("System.currentTimeMillis : {}", Instant.ofEpochMilli(System.currentTimeMillis()));
        log.info("LocalDate.now()          : {}", LocalDate.now());
        log.info("Instant.now()            : {}", Instant.now());
        log.info("===================");
    }
}
