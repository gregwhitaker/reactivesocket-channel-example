package com.github.gregwhitaker.rschannel;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.reactivesocket.Frame;
import io.reactivesocket.Payload;
import io.reactivesocket.RequestHandler;
import io.reactivesocket.netty.tcp.server.ReactiveSocketServerHandler;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

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
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    private void startServer(InetSocketAddress local, InetSocketAddress remote) throws Exception {
        ServerBootstrap localServer = new ServerBootstrap();
        localServer.group(new NioEventLoopGroup(1), new NioEventLoopGroup(4))
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(ReactiveSocketServerHandler.create((setupPayload, reactiveSocket) -> {
                            return new RequestHandler() {
                                @Override
                                public Publisher<Payload> handleRequestResponse(Payload payload) {
                                    return null;
                                }

                                @Override
                                public Publisher<Payload> handleRequestStream(Payload payload) {
                                    return null;
                                }

                                @Override
                                public Publisher<Payload> handleSubscription(Payload payload) {
                                    return null;
                                }

                                @Override
                                public Publisher<Void> handleFireAndForget(Payload payload) {
                                    return null;
                                }

                                @Override
                                public Publisher<Payload> handleChannel(Payload initialPayload, Publisher<Payload> inputs) {
                                    return s -> {
                                        Timer timer = new Timer("ping");
                                        timer.schedule(new PingTask(name, s), RANDOM.nextInt((3000 - 1) + 1) + 1, 1000);
                                    };
                                }

                                @Override
                                public Publisher<Void> handleMetadataPush(Payload payload) {
                                    return null;
                                }
                            };
                        }));
                    }
                });

        localServer.bind(local).sync();
    }

    static class PingTask extends TimerTask {

        private Subscriber<? super Payload> s;
        private final String message;

        public PingTask(String name, Subscriber<? super Payload> s) {
            this.s = s;
            this.message = String.format("[%s] - Ping", name);
        }

        @Override
        public void run() {
            s.onNext(new Payload() {
                @Override
                public ByteBuffer getData() {
                    return ByteBuffer.wrap(message.getBytes());
                }

                @Override
                public ByteBuffer getMetadata() {
                    return Frame.NULL_BYTEBUFFER;
                }
            });
        }
    }
}
