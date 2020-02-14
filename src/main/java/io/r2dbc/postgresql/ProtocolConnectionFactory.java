package io.r2dbc.postgresql;

import io.r2dbc.postgresql.authentication.AuthenticationHandler;
import io.r2dbc.postgresql.authentication.PasswordAuthenticationHandler;
import io.r2dbc.postgresql.authentication.SASLAuthenticationHandler;
import io.r2dbc.postgresql.client.ConnectionResources;
import io.r2dbc.postgresql.client.ProtocolConnection;
import io.r2dbc.postgresql.client.SSLConfig;
import io.r2dbc.postgresql.client.SSLMode;
import io.r2dbc.postgresql.client.StartupMessageFlow;
import io.r2dbc.postgresql.message.backend.AuthenticationMessage;
import io.r2dbc.postgresql.util.Assert;
import reactor.core.publisher.Mono;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.annotation.Nullable;

import java.net.SocketAddress;
import java.util.Map;
import java.util.function.Predicate;

public abstract class ProtocolConnectionFactory {

    public Mono<ProtocolConnection> connect(SocketAddress endpoint, PostgresqlConnectionConfiguration configuration, @Nullable Map<String, String> options) {
        SSLConfig sslConfig = configuration.getSslConfig();
        Predicate<Throwable> isAuthSpecificationError = e -> e instanceof ExceptionFactory.PostgresqlAuthenticationFailure;
        return this.tryConnectWithConfig(endpoint, sslConfig, configuration, options)
            .onErrorResume(
                isAuthSpecificationError.and(e -> sslConfig.getSslMode() == SSLMode.ALLOW),
                e -> this.tryConnectWithConfig(endpoint, sslConfig.mutateMode(SSLMode.REQUIRE), configuration, options)
                    .onErrorResume(sslAuthError -> {
                        e.addSuppressed(sslAuthError);
                        return Mono.error(e);
                    })
            )
            .onErrorResume(
                isAuthSpecificationError.and(e -> sslConfig.getSslMode() == SSLMode.PREFER),
                e -> this.tryConnectWithConfig(endpoint, sslConfig.mutateMode(SSLMode.DISABLE), configuration, options)
                    .onErrorResume(sslAuthError -> {
                        e.addSuppressed(sslAuthError);
                        return Mono.error(e);
                    })
            );
    }

    protected abstract Mono<? extends ProtocolConnection> connect(SocketAddress socketAddress, ConnectionResources connectionResources);

    private Mono<ProtocolConnection> tryConnectWithConfig(SocketAddress endpoint, SSLConfig sslConfig, PostgresqlConnectionConfiguration configuration, @Nullable Map<String, String> options) {
        ConnectionResources connectionResources = new ConnectionResources(configuration.getConnectTimeout(), ConnectionProvider.newConnection(), sslConfig);
        return this.connect(endpoint, connectionResources)
            .delayUntil(client -> StartupMessageFlow
                .exchange(configuration.getApplicationName(), authenticationMessage -> getAuthenticationHandler(authenticationMessage, configuration), client, configuration.getDatabase(),
                    configuration.getUsername(), options)
                .handle(ExceptionFactory.INSTANCE::handleErrorResponse))
            .cast(ProtocolConnection.class);
    }

    static AuthenticationHandler getAuthenticationHandler(AuthenticationMessage message, PostgresqlConnectionConfiguration configuration) {
        if (PasswordAuthenticationHandler.supports(message)) {
            CharSequence password = Assert.requireNonNull(configuration.getPassword(), "Password must not be null");
            return new PasswordAuthenticationHandler(password, configuration.getUsername());
        } else if (SASLAuthenticationHandler.supports(message)) {
            CharSequence password = Assert.requireNonNull(configuration.getPassword(), "Password must not be null");
            return new SASLAuthenticationHandler(password, configuration.getUsername());
        } else {
            throw new IllegalStateException(String.format("Unable to provide AuthenticationHandler capable of handling %s", message));
        }
    }
}

