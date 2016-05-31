/*
 * Copyright 2016 Greg Whitaker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import rx.Observable;
import rx.RxReactiveStreams;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Creates a simple server that communicates with other servers over two reactive socket channels.  Each server
 * creates one channel that is sending data bi-directionally.  This means there are actually two channels each server
 * is interacting with over a single reactivesocket; servers act as both a client and a server for one another.
 *
 * Server1 initiates a channel with Server2 making Server1 the client:
 *      [server1] --> YO 1 --> [server2]
 *      [server1] <-- SUP  <-- [server2]

 *
 * Server2 initiates a channel with Server1 making Server2 the client:
 *      [server2] --> YO 1 --> [server1]
 *      [server2] <-- SUP  <-- [server1]
 */
public class Server {
    final String name;

    public Server(String name) {
        this.name = name;
    }

    /**
     * Starts the server.
     *
     * @param local the address and port that this server instance binds to
     * @param remote the remote address and port that this server will communicate with when acting as a client
     */
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

                                // Creates the (server) publisher that the client will use to get the subscription
                                // needed to communicate with the server.
                                return new Publisher<Payload>() {

                                    @Override
                                    public void subscribe(Subscriber<? super Payload> s) {

                                        // Creates the (server) subscriber that the client will use to communicate
                                        // with the server.
                                        Subscriber<Payload> subscriber = new Subscriber<Payload>() {
                                            Subscription subscription;

                                            @Override
                                            public void onSubscribe(Subscription s) {
                                                subscription = s;

                                                // Having to request 2 pieces of data at a time because there is a bug
                                                // in the reactivesocket channel.  This is actually only requesting one
                                                // piece of data because of the bug causing it to be off by one.
                                                subscription.request(2);
                                            }

                                            @Override
                                            public void onNext(Payload payload) {
                                                ByteBuf buffer = Unpooled.buffer(payload.getData().capacity());
                                                buffer.writeBytes(payload.getData());

                                                byte[] bytes = new byte[buffer.capacity()];
                                                buffer.readBytes(bytes);
                                                System.out.println("[" + name + "] --> " + new String(bytes));

                                                // The server takes the client's subscriber and sends a response to it.
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

                                                // Need to keep requesting more data otherwise the server
                                                // will stop responding to events.
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

                                        // Create a subscription on the client's publisher so that the server
                                        // can recieve data from the client.
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

        // Defining what payload will be sent to the remote server
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
                        )
                );

        // Sending the payload to the remote server
        Publisher<Payload> responseStream = reactiveSocket
                .requestChannel(requestStream);

        // Printing the response from the remote server
        RxReactiveStreams
                .toObservable(responseStream)
                .forEach(payload -> {
                    byte[] bytes = new byte[payload.getData().capacity()];
                    payload.getData().get(bytes);
                    System.out.println(new String(bytes) + " <-- [" + name + "]");
                });
    }
}