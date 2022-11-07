package com.microsocks.old;

import com.microsocks.VertxRunner;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

    // Convenience method so you can run it in your IDE
    public static void main(String[] args) {
        VertxRunner.runVerticle(Client.class);
    }

    @Override
    public void start() {
        NetClientOptions options = new NetClientOptions()
                .setSsl(true)
                .setKeyStoreOptions(
                        new JksOptions()
                                .setPath("clientkeystore.jks")
                                .setPassword("password")
                )
                .setTrustStoreOptions(
                        new JksOptions()
                                .setPath("clienttruststore.jks")
                                .setPassword("password")
                );

        vertx.createNetClient(options)
                .connect(1234, "localhost", res -> {
                    if (res.succeeded()) {
                        NetSocket sock = res.result();
                        sock.handler(buff -> LOGGER.info("client receiving " + buff.toString("UTF-8")));

                        // Now send some data
                        for (int i = 0; i < 10; i++) {
                            String str = "hello " + i + "\n";
                            LOGGER.info("Net client sending: " + str);
                            sock.write(str, event -> {
                                if (event.cause() != null) {
                                    LOGGER.info("ERROR event {}", event.cause());
                                }
                                LOGGER.info("event {}", event);
                            });
                        }
                    } else {
                        LOGGER.info("Failed to connect " + res.cause());
                    }
                });
    }
}
