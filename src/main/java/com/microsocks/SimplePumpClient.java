package com.microsocks;

import com.google.common.base.Preconditions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.Pump;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SimplePumpClient extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimplePumpClient.class);
    private static final AtomicLong ACTIVE_CONNECTIONS = new AtomicLong(0);

    final NetSocket serverSocket;
    final Socks5Address address;
    @Nullable NetSocket clientSocket;
    final AtomicBoolean isConnected = new AtomicBoolean(false);

    public SimplePumpClient(Vertx vertx, NetSocket serverSocket, Socks5Address address) {
        this.vertx = vertx;
        this.serverSocket = serverSocket;
        this.address = address;
    }

    boolean addActiveConnection() {
        if (isConnected.compareAndSet(false, true)) {
            long active = ACTIVE_CONNECTIONS.incrementAndGet();
            LOGGER.info("Active connections: {}", active);
            return true;
        }
        return false;
    }

    boolean removeActiveConnection() {
        if (isConnected.compareAndSet(true, false)) {
            long active = ACTIVE_CONNECTIONS.decrementAndGet();
            LOGGER.info("Active connections: {}", active);
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        NetClientOptions options = new NetClientOptions()
/*                .setSsl(true)
                .setKeyStoreOptions(
                        new JksOptions()
                                .setPath("clientkeystore.jks")
                                .setPassword("password")
                )
                .setTrustStoreOptions(
                        new JksOptions()
                                .setPath("clienttruststore.jks")
                                .setPassword("password")
                )*/;

        vertx.createNetClient(options)
                .connect(address.port, address.hostname, res -> {
                    try {
                        if (res.succeeded()) {
                            LOGGER.info("Connected to {}:{}", address.hostname, address.port, res.cause());
                            addActiveConnection();

                            clientSocket = res.result();

                            serverSocket.closeHandler(v -> {
                                if (removeActiveConnection()) {
                                    LOGGER.info("Disconnected from {}:{}", address.hostname, address.port, res.cause());
                                }
                                Preconditions.checkNotNull(clientSocket).close();
                            });
                            clientSocket.closeHandler(v -> {
                                if (removeActiveConnection()) {
                                    LOGGER.info("Disconnected from {}:{}", address.hostname, address.port, res.cause());
                                }
                                serverSocket.close();
                            });

                            Pump.pump(serverSocket, clientSocket).start();
                            Pump.pump(clientSocket, serverSocket).start();

                            Socks5Switch.sendConnectResponse(serverSocket, address, Socks5Switch.SOCKS5_STATUS_SUCCESS);
                        } else {
                            removeActiveConnection();

                            LOGGER.error("Failed to connect to {}:{}", address.hostname, address.port, res.cause());
                            Socks5Switch.sendConnectResponse(serverSocket, address, Socks5Switch.SOCKS5_STATUS_FAILURE);
                            serverSocket.close();
                        }
                    } catch (Exception e) {
                        removeActiveConnection();

                        LOGGER.error("Failed to connect to {}:{}", address.hostname, address.port, e);
                        Socks5Switch.sendConnectResponse(serverSocket, address, Socks5Switch.SOCKS5_STATUS_FAILURE);
                        serverSocket.close();
                        if (clientSocket != null) {
                            clientSocket.close();
                        }
                    }
                });
    }
}
