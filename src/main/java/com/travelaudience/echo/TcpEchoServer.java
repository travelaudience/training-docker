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

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
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

    private static final String PATH_TO_CONFIG = System.getenv("PATH_TO_CONFIG");

    private static final int DUMP_METRICS_INTERVAL_MS = 2000;

    static {
        // Make Vert.x use SLF4J.
        System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, SLF4JLogDelegateFactory.class.getName());
    }

    public static void main(final String... args) {
        if (PATH_TO_CONFIG == null) {
            LOGGER.error("PATH_TO_CONFIG is not set.");
            System.exit(1);
        }

        // Enable metrics.
        final MetricsOptions metricsOptions = new DropwizardMetricsOptions().setEnabled(true);
        // Create a new Vertx instance.
        final Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(metricsOptions));

        // Create our 'ConfigStoreOptions'.
        final ConfigStoreOptions configOptions = new ConfigStoreOptions()
                .setType("file")
                .setConfig(new JsonObject().put("path", PATH_TO_CONFIG));
        // Create our 'ConfigurationRetriever'.
        final ConfigRetriever configRetriever = ConfigRetriever.create(
                vertx,
                new ConfigRetrieverOptions().addStore(configOptions)
        );

        // Grab the config and deploy our verticle.
        configRetriever.getConfig(res -> {
            if (res.succeeded()) {
                LOGGER.info("using configuration from {}", PATH_TO_CONFIG);
                vertx.deployVerticle(new TcpEchoServer(), new DeploymentOptions().setConfig(res.result()));
            } else {
                LOGGER.error("couldn't read the configuration file", res.cause());
                vertx.close();
            }
        });
    }

    @Override
    public void start() throws Exception {
        final String host = config().getString("host");
        final int port = config().getInteger("port");

        // Create a 'NetServer' that echoes data back to the origin, and make it listen on host:port.
        createNetServer().connectHandler(this::handleConnection).listen(port, host, res -> {
            if (res.succeeded()) {
                LOGGER.info("tcp echo server is listening at {}:{}", host, port);
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

        if (config().getBoolean("debug")) {
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

        if (config().getBoolean("debug")) {
            LOGGER.debug("connection from {} is now open", socket.remoteAddress());
            socket.closeHandler((v) -> LOGGER.debug("connection from {} is now closed", socket.remoteAddress()));
        }
    }
}
