package com.stech.schat.config;

import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;

/**
 * Migrations always run against the PRIMARY (spring.flyway.url). If the primary is unreachable at start-up
 * (the very situation the fallback exists for), skip migrations instead of crashing, so the app can still
 * boot on the fallback. Any other migration error (bad SQL, checksum mismatch...) still stops the boot.
 */
@Configuration
public class FlywayStartupConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayStartupConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayException e) {
                if (isConnectionProblem(e)) {
                    log.error("Primary database unreachable — SKIPPING Flyway migrations. The app starts on the fallback "
                            + "(read-only). Restart the service once the primary is back so pending migrations are applied.", e);
                } else {
                    throw e;
                }
            }
        };
    }

    private static boolean isConnectionProblem(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ConnectException || c instanceof SocketTimeoutException || c instanceof UnknownHostException) return true;
            if (c instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && state.startsWith("08")) return true; // connection exception class
            }
        }
        return false;
    }
}
