package com.github.gregwhitaker.rschannel;

import java.net.InetSocketAddress;

/**
 * Runs the reactivesocket channel example.
 */
public class ExampleRunner {

    /**
     * Starts two servers, one for each end of the channel.
     *
     * @param args command line arguments
     * @throws Exception
     */
    public static void main(String... args) throws Exception {
        Server server1 = new Server("server1");
        server1.start(new InetSocketAddress("127.0.0.1", 8080),
                new InetSocketAddress("127.0.0.2", 8080));

        Server server2 = new Server("server2");
        server2.start(new InetSocketAddress("127.0.0.2", 8080),
                new InetSocketAddress("127.0.0.1", 8080));

        Thread.currentThread().join();
    }
}
