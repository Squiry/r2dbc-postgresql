package io.r2dbc.postgresql.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.r2dbc.postgresql.ProtocolConnectionFactory;
import io.r2dbc.postgresql.util.Assert;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpResources;
import reactor.util.annotation.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class ReactorNettyProtocolConnectionFactory extends ProtocolConnectionFactory {

    public static ReactorNettyProtocolConnectionFactory INSTANCE = new ReactorNettyProtocolConnectionFactory();

    private ReactorNettyProtocolConnectionFactory() {
    }

    @Override
    public Mono<ReactorNettyProtocolConnection> connect(SocketAddress socketAddress, ConnectionResources connectionResources) {
        Assert.requireNonNull(connectionResources, "connectionResources must not be null");
        Assert.requireNonNull(socketAddress, "socketAddress must not be null");

        TcpClient tcpClient = TcpClient.create(connectionResources.getConnectionProvider()).addressSupplier(() -> socketAddress);

        if (!(socketAddress instanceof InetSocketAddress)) {
            tcpClient = tcpClient.runOn(new SocketLoopResources(), true);
        }

        if (connectionResources.getConnectTimeout() != null) {
            tcpClient = tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectionResources.getConnectTimeout().toMillis()));
        }

        return tcpClient.connect().flatMap(it -> {
            ChannelPipeline pipeline = it.channel().pipeline();

            InternalLogger logger = InternalLoggerFactory.getInstance(ReactorNettyProtocolConnection.class);
            if (logger.isTraceEnabled()) {
                pipeline.addFirst(LoggingHandler.class.getSimpleName(), new LoggingHandler(ReactorNettyProtocolConnection.class, LogLevel.TRACE));
            }

            return sslHandshake(connectionResources.getSslConfig(), it).thenReturn(new ReactorNettyProtocolConnection(it, connectionResources));
        });
    }

    private static Mono<? extends Void> sslHandshake(SSLConfig sslConfig, Connection it) {

        if (sslConfig.getSslMode().startSsl()) {
            SSLSessionHandlerAdapter sslSessionHandlerAdapter = new SSLSessionHandlerAdapter(it.outbound().alloc(), sslConfig);
            it.addHandlerFirst(sslSessionHandlerAdapter);
            return sslSessionHandlerAdapter.getHandshake();
        }

        return Mono.empty();
    }

    static class SocketLoopResources implements LoopResources {

        @Nullable
        private static final Class<? extends Channel> EPOLL_SOCKET = findClass("io.netty.channel.epoll.EpollDomainSocketChannel");

        @Nullable
        private static final Class<? extends Channel> KQUEUE_SOCKET = findClass("io.netty.channel.kqueue.KQueueDomainSocketChannel");

        private static final boolean kqueue;

        static {
            boolean kqueueCheck = false;
            try {
                Class.forName("io.netty.channel.kqueue.KQueue");
                kqueueCheck = io.netty.channel.kqueue.KQueue.isAvailable();
            } catch (ClassNotFoundException cnfe) {
            }
            kqueue = kqueueCheck;
        }

        private static final boolean epoll;

        static {
            boolean epollCheck = false;
            try {
                Class.forName("io.netty.channel.epoll.Epoll");
                epollCheck = Epoll.isAvailable();
            } catch (ClassNotFoundException cnfe) {
            }
            epoll = epollCheck;
        }

        private final LoopResources delegate = TcpResources.get();

        @SuppressWarnings("unchecked")
        private static Class<? extends Channel> findClass(String className) {
            try {
                return (Class<? extends Channel>) SocketLoopResources.class.getClassLoader().loadClass(className);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        @Override
        public Class<? extends Channel> onChannel(EventLoopGroup group) {

            if (epoll && EPOLL_SOCKET != null) {
                return EPOLL_SOCKET;
            }

            if (kqueue && KQUEUE_SOCKET != null) {
                return KQUEUE_SOCKET;
            }

            return this.delegate.onChannel(group);
        }

        @Override
        public EventLoopGroup onClient(boolean useNative) {
            return this.delegate.onClient(useNative);
        }

        @Override
        public Class<? extends DatagramChannel> onDatagramChannel(EventLoopGroup group) {
            return this.delegate.onDatagramChannel(group);
        }

        @Override
        public EventLoopGroup onServer(boolean useNative) {
            return this.delegate.onServer(useNative);
        }

        @Override
        public Class<? extends ServerChannel> onServerChannel(EventLoopGroup group) {
            return this.delegate.onServerChannel(group);
        }

        @Override
        public EventLoopGroup onServerSelect(boolean useNative) {
            return this.delegate.onServerSelect(useNative);
        }

        @Override
        public boolean preferNative() {
            return this.delegate.preferNative();
        }

        @Override
        public boolean daemon() {
            return this.delegate.daemon();
        }

        @Override
        public void dispose() {
            this.delegate.dispose();
        }

        @Override
        public Mono<Void> disposeLater() {
            return this.delegate.disposeLater();
        }
    }

}
