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
                new InetSocketAddress("127.0.0.1", 8081));

        Server server2 = new Server("server2");
        server2.start(new InetSocketAddress("127.0.0.1", 8081),
                new InetSocketAddress("127.0.0.1", 8080));

        Thread.currentThread().join();
    }
}
