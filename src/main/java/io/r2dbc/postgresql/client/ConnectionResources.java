package io.r2dbc.postgresql.client;

import reactor.netty.resources.ConnectionProvider;
import reactor.util.annotation.Nullable;

import java.time.Duration;

public class ConnectionResources {

    @Nullable
    private final Duration connectTimeout;

    private final ConnectionProvider connectionProvider;

    private final SSLConfig sslConfig;

    public ConnectionResources(@Nullable Duration connectTimeout, ConnectionProvider connectionProvider, SSLConfig sslConfig) {
        this.connectTimeout = connectTimeout;
        this.connectionProvider = connectionProvider;
        this.sslConfig = sslConfig;
    }

    @Nullable
    public Duration getConnectTimeout() {
        return this.connectTimeout;
    }

    public ConnectionProvider getConnectionProvider() {
        return this.connectionProvider;
    }

    public SSLConfig getSslConfig() {
        return this.sslConfig;
    }
}
