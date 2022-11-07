package com.microsocks;

import com.google.common.base.Preconditions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.UnknownHostException;

import static com.microsocks.Socks5Address.SOCKS5_ADDRESS_HOSTNAME;
import static com.microsocks.Socks5Address.SOCKS5_ADDRESS_IPV4;
import static com.microsocks.Socks5Address.SOCKS5_ADDRESS_IPV6;

public class Socks5Switch<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimplePumpClient.class);

    private final static short SOCKS5_VERSION = 5;
    private final static short SOCKS5_AUTH_NONE = 0;
    private final static short FAILURE = 0xFF;

    private final static int SOCKS5_COMMAND_CONNECT = 1;
    private final static int SOCKS5_COMMAND_RESOLV = 0xF0;
    private final static int SOCKS5_COMMAND_RESOLV_PTR = 0xF1;

    public final static int SOCKS5_STATUS_SUCCESS = 0;
    public final static int SOCKS5_STATUS_FAILURE = 1;
    public final static int SOCKS5_STATUS_CONNECTION_REFUSED = 5;
    public final static int SOCKS5_STATUS_COMMAND_NOT_SUPPORTED = 7;

    enum Stage {
        NOT_INITIALIZED,
        AUTHENTICATED,
        CLIENT_STARTED
    }

    private static void sendFailure(NetSocket socket) {
        socket.write(Buffer.buffer(new byte[] { (byte)SOCKS5_VERSION, (byte) FAILURE}));
    }

    private static void sendAuthSuccess(NetSocket socket) {
        socket.write(Buffer.buffer(new byte[] { (byte)SOCKS5_VERSION, (byte)SOCKS5_AUTH_NONE }));
    }

    public static void sendConnectResponse(NetSocket socket, Socks5Address address, int status) {
        byte[] addressBytes = address.addressBytes;

        Buffer buffer = Buffer.buffer(6 + addressBytes.length + (address.addressType == SOCKS5_ADDRESS_HOSTNAME ? 1 : 0));
        buffer
                .appendByte((byte)SOCKS5_VERSION)
                .appendByte((byte)status)
                .appendByte((byte)0)//reserved
                .appendByte(address.addressType);
        if (address.addressType == SOCKS5_ADDRESS_HOSTNAME) {
            buffer.appendByte((byte)addressBytes.length);
        }
        buffer.appendBytes(addressBytes);
        buffer.appendShort((short)address.port);

        socket.write(buffer);
    }

    final Vertx vertx;
    final NetSocket serverSocket;

    @Nullable NetSocket clientSocket;
    @Nullable
    Socks5Address outgoingAddress;
    Stage stage;

    Socks5Switch(Vertx vertx, NetSocket serverSocket) {
        this.vertx = vertx;
        this.serverSocket = serverSocket;
        this.stage = Stage.NOT_INITIALIZED;

        this.clientSocket = null;
        this.outgoingAddress = null;

        initialize();
    }

    void failInit() {
        sendFailure(serverSocket);
        serverSocket.close();
    }

    void authSuccess() {
        sendFailure(serverSocket);
        serverSocket.close();
    }

    /**
     * We only support no Auth
     */
    private void authenticate(Buffer data) {
        int pos = 0;

        short version = data.getUnsignedByte(pos++);
        if (version != SOCKS5_VERSION) {
            LOGGER.error("Authentication failed. Version mismatch {}", version);
            failInit();
            return;
        }

        short methodsCount = data.getUnsignedByte(pos++);
        byte[] methods = new byte[methodsCount];
        data.getBytes(pos, pos + methodsCount, methods);
        //pos += methodsCount;

        boolean noAuthMethodFound = false;
        for (byte method : methods) {
            if (method == SOCKS5_AUTH_NONE) {
                noAuthMethodFound = true;
                break;
            }
        }

        if (!noAuthMethodFound) {
            LOGGER.error("Authentication failed. Auth method \"None\" not found");
            failInit();
            return;
        }

        sendAuthSuccess(serverSocket);

        stage = Stage.AUTHENTICATED;
    }

    private void initOutgoingConnection(Buffer data) throws UnknownHostException {
        int pos = 0;

        short version = data.getUnsignedByte(pos++);
        if (version != SOCKS5_VERSION) {
            LOGGER.error("Initialization failed. Version mismatch {}", version);
            failInit();
            return;
        }

        short command = data.getUnsignedByte(pos++);
        pos++;//1 byte reserved

        if (command != SOCKS5_COMMAND_CONNECT) {
            LOGGER.error("Initialization failed. Command mismatch {}", command);
            failInit();
            return;
        }

        short addressType = data.getUnsignedByte(pos++);

        switch (addressType) {
            case SOCKS5_ADDRESS_IPV4:
                byte[] ipv4Bytes = new byte[4];
                data.getBytes(pos, pos + 4, ipv4Bytes);
                pos += 4;

                outgoingAddress = Socks5Address.createIpv4(ipv4Bytes);
                break;
            case SOCKS5_ADDRESS_IPV6:
                byte[] ipv6Bytes = new byte[16];
                data.getBytes(pos, pos + 16, ipv6Bytes);
                pos += 16;

                outgoingAddress = Socks5Address.createIpv6(ipv6Bytes);
                break;
            case SOCKS5_ADDRESS_HOSTNAME:
                short length = data.getUnsignedByte(pos++);
                byte[] hostnameBytes = new byte[length];
                data.getBytes(pos, pos + length, hostnameBytes);
                pos += length;

                outgoingAddress = Socks5Address.createHostname(hostnameBytes);
                break;
            default:
                LOGGER.error("Initialization failed. Address type unknown {}", addressType);
                failInit();
                return;
        }

        outgoingAddress.port = data.getUnsignedShort(pos);
        //pos += 2;

        stage = Stage.CLIENT_STARTED;

        startClient();
    }

    private void startClient() {
        new SimplePumpClient(vertx, serverSocket, Preconditions.checkNotNull(outgoingAddress)).start();
    }

    public void initialize() {
        serverSocket.handler(data -> {
            try {
                //TODO: care about partial buffers?
                if (stage == Stage.NOT_INITIALIZED) {
                    authenticate(data);
                } else if (stage == Stage.AUTHENTICATED) {
                    initOutgoingConnection(data);
                }
            } catch(Exception e) {
                LOGGER.error("Initialization failed", e);
                failInit();
            }
        });
    }
}
