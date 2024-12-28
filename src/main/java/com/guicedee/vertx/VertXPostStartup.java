package com.guicedee.vertx;

import com.guicedee.client.Environment;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedinjection.interfaces.IGuicePostStartup;
import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import com.guicedee.vertx.spi.VertxHttpServerConfigurator;
import com.guicedee.vertx.spi.VertxHttpServerOptionsConfigurator;
import com.guicedee.vertx.spi.VertxRouterConfigurator;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.java.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static com.guicedee.vertx.spi.VertXPreStartup.vertx;

@Singleton
@Getter
@Log
public class VertXPostStartup implements IGuicePostStartup<VertXPostStartup>, IGuicePreDestroy<VertXPostStartup> {
    @Override
    public List<CompletableFuture<Boolean>> postLoad() {
        return List.of(CompletableFuture.supplyAsync(() -> {
            HttpServerOptions serverOptions = new HttpServerOptions();
            serverOptions.setCompressionSupported(true);
            serverOptions.setCompressionLevel(9);
            serverOptions.setWebSocketCompressionLevel(9);
            serverOptions.setTcpKeepAlive(true);
            ServiceLoader<VertxHttpServerOptionsConfigurator> options = ServiceLoader.load(VertxHttpServerOptionsConfigurator.class);
            for (VertxHttpServerOptionsConfigurator option : options) {
                serverOptions = option.builder(IGuiceContext.get(serverOptions.getClass()));
            }

            List<HttpServer> httpServers = new ArrayList<>();
            if (Boolean.parseBoolean(Environment.getProperty("HTTP_ENABLED", "true"))) {
                var server = vertx.createHttpServer(serverOptions);
                serverOptions.setPort(Integer.parseInt(Environment.getSystemPropertyOrEnvironment("HTTP_PORT", "8080")));
                httpServers.add(server);
            }

            if (Boolean.parseBoolean(Environment.getProperty("HTTPS_ENABLED", "false"))) {
                serverOptions.setSsl(true).setUseAlpn(true);
                serverOptions.setPort(Integer.parseInt(Environment.getSystemPropertyOrEnvironment("HTTPS_PORT", "443")));
                if (Environment.getSystemPropertyOrEnvironment("HTTPS_KEYSTORE", "").toLowerCase().endsWith("pfx") ||
                        Environment.getSystemPropertyOrEnvironment("HTTPS_KEYSTORE", "").toLowerCase().endsWith("p12") ||
                        Environment.getSystemPropertyOrEnvironment("HTTPS_KEYSTORE", "").toLowerCase().endsWith("p8")
                ) {
                    serverOptions
                            .setKeyCertOptions(new PfxOptions()
                                    .setPassword(Environment.getSystemPropertyOrEnvironment("HTTPS_KEYSTORE_PASSWORD", ""))
                                    .setPath(Environment.getSystemPropertyOrEnvironment("HTTPS_KEYSTORE", "keystore.pfx")));

                } else if (Environment.getSystemPropertyOrEnvironment("HTTPS_KEYSTORE", "").toLowerCase().endsWith("jks")) {
                    serverOptions
                            .setKeyCertOptions(new JksOptions()
                                    .setPassword(Environment.getSystemPropertyOrEnvironment("HTTPS_KEYSTORE_PASSWORD", "changeit"))
                                    .setPath(Environment.getSystemPropertyOrEnvironment("HTTPS_KEYSTORE", "keystore.jks")));
                }
                var server = vertx.createHttpServer(serverOptions);
                httpServers.add(server);
            }

            ServiceLoader<VertxHttpServerConfigurator> servers = ServiceLoader.load(VertxHttpServerConfigurator.class);
            for (VertxHttpServerConfigurator a : servers) {
                for (var s : httpServers) {
                    IGuiceContext.get(a.getClass())
                            .builder(s);
                }
            }

            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create().setUploadsDirectory("uploads").setDeleteUploadedFilesOnEnd(true));
            ServiceLoader<VertxRouterConfigurator> routes = ServiceLoader.load(VertxRouterConfigurator.class);
            for (VertxRouterConfigurator route : routes) {
                router = IGuiceContext.get(route.getClass()).builder(router);
            }
            IJsonRepresentation.configureObjectMapper(DatabindCodec.mapper());

            for (var s : httpServers) {
                s.requestHandler(router);
            }

            for (var s : httpServers) {
                s.listen().onComplete(handler->{
                    if (handler.failed()) {
                        log.log(Level.SEVERE,"Cannot start listener",handler.cause());
                    }
                });
            }

            return true;
        }, getExecutorService()));
    }

    public Vertx getVertx() {
        return vertx;
    }

    @Override
    public void onDestroy() {
        vertx.close();
    }

    @Override
    public Integer sortOrder() {
        return Integer.MIN_VALUE + 500;
    }
}
