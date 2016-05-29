package com.github.gregwhitaker.rschannel;

import io.netty.bootstrap.ServerBootstrap;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.RxReactiveStreams;

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
                            return new RequestHandler.Builder().withRequestChannel(payloadPublisher -> {
                                return new Publisher<Payload>() {
                                    @Override
                                    public void subscribe(Subscriber<? super Payload> s) {
                                        s.onNext(new Payload() {
                                            @Override
                                            public ByteBuffer getData() {
                                                return ByteBuffer.wrap(String.format("[%s] - YO", name).getBytes());
                                            }

                                            @Override
                                            public ByteBuffer getMetadata() {
                                                return Frame.NULL_BYTEBUFFER;
                                            }
                                        });
                                    }
                                };
                            }).build();
                        }));
                    }
                });

        localServer.bind(local).sync();

        Publisher<ClientTcpDuplexConnection> publisher = ClientTcpDuplexConnection
                .create(remote, new NioEventLoopGroup(1));

        ClientTcpDuplexConnection duplexConnection = RxReactiveStreams.toObservable(publisher).toBlocking().last();
        ReactiveSocket reactiveSocket = DefaultReactiveSocket.fromClientConnection(duplexConnection,
                ConnectionSetupPayload.create("UTF-8", "UTF-8"), t -> t.printStackTrace());

        reactiveSocket.startAndWait();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                reactiveSocket.requestChannel(s -> {
                    s.onNext(new Payload() {
                        @Override
                        public ByteBuffer getData() {
                            return ByteBuffer.wrap(String.format("[%s] - YO", name).getBytes());
                        }

                        @Override
                        public ByteBuffer getMetadata() {
                            return Frame.NULL_BYTEBUFFER;
                        }
                    });
                });
            }
        }, RANDOM.nextInt((3000 - 1) + 1) + 1, 1000);
    }
}