package com.stech.schat.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Primary/fallback DataSource.
 *
 * <p>Every new connection is taken from the PRIMARY pool (Supabase). If the primary cannot hand out a
 * connection (unreachable, timing out, refusing), it is marked down, new connections come from the FALLBACK
 * pool (Neon) and a background probe checks the primary every few seconds. As soon as the primary answers
 * {@code SELECT 1} again, new connections go back to it. A transaction that already holds a connection is never
 * moved, so a single request always sees one database.
 *
 * <p>The fallback is expected to be a read-only replica (see docs/DATABASE_FAILOVER.md). When it is configured
 * read-only, writes fail with SQLState 25006, which the API reports as HTTP 503 "read-only mode".
 */
public class FailoverDataSource extends AbstractDataSource implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FailoverDataSource.class);

    private final HikariDataSource primary;
    private final HikariDataSource fallback; // may be null (no fallback configured)
    private volatile boolean primaryHealthy = true;
    private final ScheduledExecutorService prober;

    public FailoverDataSource(HikariDataSource primary, HikariDataSource fallback, int probeIntervalSeconds) {
        this.primary = primary;
        this.fallback = fallback;
        this.prober = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-failover-probe");
            t.setDaemon(true);
            return t;
        });
        if (fallback != null) {
            prober.scheduleWithFixedDelay(this::probePrimaryIfDown, probeIntervalSeconds, probeIntervalSeconds, TimeUnit.SECONDS);
        }
    }

    /** True while connections are being served by the fallback database. */
    public boolean isUsingFallback() {
        return fallback != null && !primaryHealthy;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (primaryHealthy || fallback == null) {
            try {
                return primary.getConnection();
            } catch (SQLException e) {
                if (fallback == null) throw e;
                markPrimaryDown(e);
            }
        }
        return fallback.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection(); // credentials are fixed per pool
    }

    private synchronized void markPrimaryDown(SQLException cause) {
        if (primaryHealthy) {
            primaryHealthy = false;
            log.error("PRIMARY DATABASE UNAVAILABLE — switching new connections to the fallback database. Cause: {}",
                    cause.toString());
        }
    }

    private void probePrimaryIfDown() {
        if (primaryHealthy) return;
        try (Connection c = primary.getConnection(); Statement s = c.createStatement()) {
            s.setQueryTimeout(3);
            s.execute("SELECT 1");
            primaryHealthy = true;
            log.warn("Primary database is reachable again — new connections are back on the primary.");
        } catch (Exception e) {
            log.debug("Primary still unavailable: {}", e.toString());
        }
    }

    @Override
    public void destroy() {
        prober.shutdownNow();
        primary.close();
        if (fallback != null) fallback.close();
    }
}
