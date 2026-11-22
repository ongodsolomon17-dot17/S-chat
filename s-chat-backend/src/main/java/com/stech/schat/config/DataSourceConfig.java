package com.stech.schat.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Builds the primary (Supabase) and optional fallback (Neon) connection pools.
 * Leave NEON_DB_URL empty to run on the primary only.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @Primary
    public FailoverDataSource dataSource(
            @Value("${app.db.primary.url}") String primaryUrl,
            @Value("${app.db.primary.username}") String primaryUser,
            @Value("${app.db.primary.password}") String primaryPassword,
            @Value("${app.db.fallback.url:}") String fallbackUrl,
            @Value("${app.db.fallback.username:}") String fallbackUser,
            @Value("${app.db.fallback.password:}") String fallbackPassword,
            @Value("${app.db.fallback.read-only:true}") boolean fallbackReadOnly,
            @Value("${app.db.pool-size:8}") int poolSize,
            @Value("${app.db.primary.connect-timeout-ms:4000}") long connectTimeoutMs,
            @Value("${app.db.probe-interval-seconds:10}") int probeIntervalSeconds
    ) {
        HikariDataSource primary = pool("primary", primaryUrl, primaryUser, primaryPassword, poolSize, 1, connectTimeoutMs, false);
        HikariDataSource fallback = null;
        if (fallbackUrl != null && !fallbackUrl.isBlank()) {
            // Minimum idle 0: never keep warm connections to the standby; it only connects when actually needed.
            fallback = pool("fallback", fallbackUrl, fallbackUser, fallbackPassword, poolSize, 0, 15000, fallbackReadOnly);
            log.info("Database failover ENABLED (fallback is {}).", fallbackReadOnly ? "read-only" : "READ-WRITE — risk of divergent data");
        } else {
            log.info("Database failover disabled (no fallback URL configured).");
        }
        return new FailoverDataSource(primary, fallback, probeIntervalSeconds);
    }

    private static HikariDataSource pool(String name, String url, String user, String password, int size, int minIdle,
                                         long connectionTimeoutMs, boolean readOnly) {
        HikariConfig c = new HikariConfig();
        c.setPoolName("schat-" + name);
        c.setJdbcUrl(url);
        c.setUsername(user);
        c.setPassword(password);
        c.setMaximumPoolSize(size);
        c.setMinimumIdle(minIdle);
        c.setConnectionTimeout(Math.max(250, connectionTimeoutMs));
        c.setMaxLifetime(240_000);   // recycle before pooler / scale-to-zero closes the connection
        c.setIdleTimeout(60_000);
        c.setReadOnly(readOnly);
        // Do not fail application start-up if a database is down; that is exactly the case failover handles.
        c.setInitializationFailTimeout(-1);
        // Both Supabase's pooler (transaction mode) and Neon's pooler are PgBouncer-style: no server-side prepared statements.
        c.addDataSourceProperty("prepareThreshold", "0");
        c.addDataSourceProperty("connectTimeout", "5");   // seconds, TCP connect
        c.addDataSourceProperty("socketTimeout", "30");   // seconds, a hung server cannot block a request forever
        c.addDataSourceProperty("tcpKeepAlive", "true");
        return new HikariDataSource(c);
    }
}
