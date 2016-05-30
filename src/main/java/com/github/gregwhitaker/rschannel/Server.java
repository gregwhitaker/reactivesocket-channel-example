package com.github.gregwhitaker.rschannel;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.reactivesocket.ConnectionSetupPayload;
import io.reactivesocket.DefaultReactiveSocket;
import io.reactivesocket.Frame;
import io.reactivesocket.Payload;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.RequestHandler;
import io.reactivesocket.netty.tcp.client.ClientTcpDuplexConnection;
import io.reactivesocket.netty.tcp.server.ReactiveSocketServerHandler;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.RxReactiveStreams;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Server {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    final String name;

    public Server(String name) {
        this.name = name;
    }

    public void start(InetSocketAddress local, InetSocketAddress remote) {
        Thread thread = new Thread(() -> {
            Server server = new Server(name);
            try {
                server.startServer(local, remote);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    private void startServer(InetSocketAddress local, InetSocketAddress remote) throws Throwable {
        ServerBootstrap server = new ServerBootstrap();
        server.group(new NioEventLoopGroup(1), new NioEventLoopGroup(4))
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(ReactiveSocketServerHandler.create((setupPayload, reactiveSocket) -> {
                            return new RequestHandler.Builder().withRequestChannel(payloadPublisher -> {

                                // This is the gift of the publisher from the server side (on the client)
                                return new Publisher<Payload>() {

                                    @Override
                                    public void subscribe(Subscriber<? super Payload> s) {

                                        // This is the server side subscriber (on the client)
                                        Subscriber<Payload> subscriber = new Subscriber<Payload>() {
                                            Subscription subscription;

                                            @Override
                                            public void onSubscribe(Subscription s) {
                                                subscription = s;
                                                subscription.request(2);
                                            }

                                            @Override
                                            public void onNext(Payload payload) {
                                                ByteBuf buffer = Unpooled.buffer(payload.getData().capacity());
                                                buffer.writeBytes(payload.getData());

                                                byte[] bytes = new byte[buffer.capacity()];
                                                buffer.readBytes(bytes);
                                                System.out.println("[" + name + "] --> " + new String(bytes));

                                                // This talks back to the client
                                                s.onNext(new Payload() {
                                                    @Override
                                                    public ByteBuffer getData() {
                                                        return ByteBuffer.wrap(("SUP").getBytes());
                                                    }

                                                    @Override
                                                    public ByteBuffer getMetadata() {
                                                        return Frame.NULL_BYTEBUFFER;
                                                    }
                                                });

                                                subscription.request(1);
                                            }

                                            @Override
                                            public void onError(Throwable t) {
                                                s.onError(t);
                                            }

                                            @Override
                                            public void onComplete() {

                                            }
                                        };

                                        payloadPublisher.subscribe(subscriber);
                                    }
                                };

                            }).build();
                        }));
                    }
                });

        server.bind(local).sync();

        Publisher<ClientTcpDuplexConnection> publisher = ClientTcpDuplexConnection
                .create(remote, new NioEventLoopGroup(1));

        ClientTcpDuplexConnection duplexConnection = RxReactiveStreams.toObservable(publisher).toBlocking().last();
        ReactiveSocket reactiveSocket = DefaultReactiveSocket.fromClientConnection(duplexConnection,
                ConnectionSetupPayload.create("UTF-8", "UTF-8"), t -> t.printStackTrace());

        reactiveSocket.startAndWait();

        Publisher<Payload> requestStream = RxReactiveStreams
                .toPublisher(Observable
                        .interval(1_000, TimeUnit.MILLISECONDS)
                        .onBackpressureDrop()
                        .map(i ->
                                new Payload() {
                                    @Override
                                    public ByteBuffer getData() {
                                        return ByteBuffer.wrap(("YO " + i).getBytes());
                                    }

                                    @Override
                                    public ByteBuffer getMetadata() {
                                        return Frame.NULL_BYTEBUFFER;
                                    }
                                }
                        ));

        Publisher<Payload> responseStream = reactiveSocket
                .requestChannel(requestStream);

        RxReactiveStreams
                .toObservable(responseStream)
                .forEach(payload -> {
                    byte[] bytes = new byte[payload.getData().capacity()];
                    payload.getData().get(bytes);
                    System.out.println(new String(bytes) + " <-- [" + name + "]");
                });
    }
}