/*
 *  Copyright 2017 The 'tcp-echo-server' Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.travelaudience.echo;

import static io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.Pump;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class TcpEchoServer extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpEchoServer.class);

    private static final boolean DEBUG = Environment.getBoolean("DEBUG", false);
    private static final String HOST = Environment.getString("HOST", "0.0.0.0");
    private static final int PORT = Environment.getInteger("PORT", 7);

    private static final int DUMP_METRICS_INTERVAL_MS = 2000;

    static {
        // Make Vert.x use SLF4J.
        System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, SLF4JLogDelegateFactory.class.getName());
    }

    public static void main(final String... args) {
        // Enable metrics.
        final MetricsOptions metricsOptions = new DropwizardMetricsOptions().setEnabled(true);
        // Create a new Vertx instance and deploy our verticle.
        Vertx.vertx(new VertxOptions().setMetricsOptions(metricsOptions)).deployVerticle(new TcpEchoServer());
    }

    @Override
    public void start() throws Exception {
        // Create a 'NetServer' that echoes data back to the origin, and make it listen on HOST:PORT.
        createNetServer().connectHandler(this::handleConnection).listen(PORT, HOST, res -> {
            if (res.succeeded()) {
                LOGGER.info("tcp echo server is listening at {}:{}", HOST, PORT);
            } else {
                LOGGER.error("echo server failed to start", res.cause());
            }
        });
    }

    private void dumpMetrics(final JsonObject metrics) {
        // Grab the number of open connections.
        final long openConnections = metrics.getJsonObject("open-netsockets").getLong("count");
        // Output the metrics.
        LOGGER.debug("open connections: {}", openConnections);
    }

    private NetServer createNetServer() {
        final NetServer server = vertx.createNetServer();

        if (DEBUG) {
            // Create a 'MetricsService' for the underlying Vertx instance.
            final MetricsService metrics = MetricsService.create(this.vertx);
            // Create a 'ScheduledExecutorService' with which to dump metrics in background.
            final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            // Create a 'Runnable' which will dump metrics about the 'NetServer'.
            final Runnable task = () -> dumpMetrics(metrics.getMetricsSnapshot(server));
            // Schedule the task at a fixed rate of 'DUMP_METRICS_INTERVAL_MS' (and equal initial delay).
            executor.scheduleAtFixedRate(task, DUMP_METRICS_INTERVAL_MS, DUMP_METRICS_INTERVAL_MS, MILLISECONDS);
        }

        return server;
    }

    private void handleConnection(final NetSocket socket) {
        Pump.pump(socket, socket).start();

        if (DEBUG) {
            LOGGER.debug("connection from {} is now open", socket.remoteAddress());
            socket.closeHandler((v) -> LOGGER.debug("connection from {} is now closed", socket.remoteAddress()));
        }
    }
}
