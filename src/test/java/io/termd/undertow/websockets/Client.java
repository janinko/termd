package io.termd.undertow.websockets;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * @see "https://github.com/undertow-io/undertow/blob/5bdddf327209a4abf18792e78148863686c26e9b/websockets-jsr/src/test/java/io/undertow/websockets/jsr/test/BinaryEndpointTest.java"
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Client {

    ProgramaticClientEndpoint endpoint = new ProgramaticClientEndpoint();
    private Consumer<Session> onOpenConsumer;
    private Consumer<String> onStringMessageConsumer;
    private Consumer<byte[]> onBinaryMessageConsumer;
    private Consumer<CloseReason> onCloseConsumer;
    private Consumer<Throwable> onErrorConsumer;

    public Endpoint connect(String websocketUrl) throws Exception {
        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        ContainerProvider.getWebSocketContainer().connectToServer(endpoint, clientEndpointConfig, new URI(websocketUrl));
        return endpoint;
    }

    public void close() throws Exception {
        System.out.println("Client is closing connection..."); //TODO log debug
        endpoint.session.close();
//        endpoint.closeLatch.await(10, TimeUnit.SECONDS);
    }

    public void onOpen(Consumer<Session> onOpen) {
        onOpenConsumer = onOpen;
    }

    public void onStringMessage(Consumer<String> onStringMessage) {
        onStringMessageConsumer = onStringMessage;
    }

    public void onBinaryMessage(Consumer<byte[]> onBinaryMessage) {
        onBinaryMessageConsumer = onBinaryMessage;
    }

    public void onClose(Consumer<CloseReason> onClose) {
        onCloseConsumer = onClose;
    }

    public void onError(Consumer<Throwable> onError) {
        onErrorConsumer = onError;
    }

    public RemoteEndpoint.Basic getRemoteEndpoint() {
        return endpoint.session.getBasicRemote();
    }

    public class ProgramaticClientEndpoint extends Endpoint {
        final CountDownLatch closeLatch = new CountDownLatch(1); //TODO do we need latch ?
        volatile Session session;

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            System.out.println("> Client received open.");
            this.session = session;

            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    System.out.println("> Client received message:" + message);
                    if (onStringMessageConsumer != null) {
                        onStringMessageConsumer.accept(message);
                    }
                }
            });
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                @Override
                public void onMessage(byte[] bytes) {
                    System.out.println("> Client received binary message:" + new String(bytes));
                    if (onBinaryMessageConsumer != null) {
                        onBinaryMessageConsumer.accept(bytes);
                    }
                }
            });
            if (onOpenConsumer != null) {
                onOpenConsumer.accept(session);
            }
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            System.out.println("> Client received close.");
            onCloseConsumer.accept(closeReason);
//            closeLatch.countDown();
        }

        @Override
        public void onError(Session session, Throwable thr) {
            if (onErrorConsumer != null) {
                onErrorConsumer.accept(thr);
            } else {
                thr.printStackTrace(); //TODO log
            }
        }
    }

}
