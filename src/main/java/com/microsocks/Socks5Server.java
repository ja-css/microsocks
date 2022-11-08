package com.microsocks;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.NetServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.util.Base64;

public class Socks5Server extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(Socks5Server.class);

    private static final String PK = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnpweg/Hr+X2I8YgLk3I2RqPSpBCbpGHzpF4W6cZ/ZOGQbPqIYMJ+hAhIw3BPwsAg6YpAuDIiUvM81bmdjZMIihfNxkKt1Za0/2sTrgfxBMNI5+BcirQd/aMHwhW+CxvL3NyJkSPPnRrEKUoOHMK71K2v3ShsU5T9iBFCPo6opC9wmosgKYWeWiUCajOZKbGReXAQO0Sxb+TProKTvT9zksvLYlR9HKKde4FJe1RiesgWgjMwPXrrV7xSjPTaTtp9eizYF88AoCJK3EC7H8YwrkV6hh/oJnN0R4yCXKGqf7wX3esPBwIKX71/O/N6Z8PG72qS+mZyyLQrdSkF3F3JzQIDAQAB";
    private static final String HOSTNAME = "localhost";
    private static final int PORT = 9150;

    static {
        String logFactory = System.getProperty("org.vertx.logger-delegate-factory-class-name");
        if (logFactory == null) {
            System.setProperty("org.vertx.logger-delegate-factory-class-name",
                    SLF4JLogDelegateFactory.class.getName());
        }
    }

    private static boolean TLS_SERVER;

    public static void setTlsServer(boolean tlsServer) {
        TLS_SERVER = tlsServer;
    }

    @Override
    public void start() {
        LOGGER.info("Starting SOCKS5 server. host {} port {}", HOSTNAME, PORT);

        NetServerOptions options = new NetServerOptions();
        if (TLS_SERVER) {
            options
                .setSsl(true)
                //Require client certificate
                .setClientAuth(ClientAuth.REQUIRED)
                .setKeyCertOptions(
                        new JksOptions()
                                .setPath("serverkeystore.jks")
                                .setPassword("password"))
                .setTrustStoreOptions(
                        new JksOptions()
                                .setPath("servertruststore.jks")
                                .setPassword("password")
                );
        }

        vertx.createNetServer(options)
                .connectHandler(sock -> {
                    if (TLS_SERVER) {
                        SSLSession sslSession = sock.sslSession();

                        try {
                            String pkFromWire = Base64.getEncoder().encodeToString(sslSession.getPeerCertificates()[0].getPublicKey().getEncoded());
                            if (!pkFromWire.equals(PK)) {
                                throw new SSLPeerUnverifiedException("MicroSocks: Client public key mismatch!");
                            }
                        } catch (SSLPeerUnverifiedException e) {
                            LOGGER.info("Client connection issue.", e);
                            sock.close();
                        }
                    }

                    new Socks5Switch<>(vertx, sock);
                }).listen(PORT, "localhost");

        LOGGER.info("SOCKS5 server is now listening on host {} port {}", HOSTNAME, PORT);
    }
}