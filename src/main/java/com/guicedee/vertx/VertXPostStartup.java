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
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

import static com.guicedee.vertx.spi.VertXPreStartup.vertx;

@Singleton
@Getter
public class VertXPostStartup implements IGuicePostStartup<VertXPostStartup>, IGuicePreDestroy<VertXPostStartup>
{
    @Override
    public List<CompletableFuture<Boolean>> postLoad()
    {
        return List.of(CompletableFuture.supplyAsync(() -> {
            HttpServerOptions serverOptions = new HttpServerOptions();
            serverOptions.setCompressionSupported(true);
            serverOptions.setCompressionLevel(9);
            serverOptions.setWebSocketCompressionLevel(9);
            serverOptions.setTcpKeepAlive(true);
            ServiceLoader<VertxHttpServerOptionsConfigurator> options = ServiceLoader.load(VertxHttpServerOptionsConfigurator.class);
            for (VertxHttpServerOptionsConfigurator option : options)
            {
                serverOptions = option.builder(IGuiceContext.get(serverOptions.getClass()));
            }
            HttpServer server = vertx.createHttpServer(serverOptions);
            ServiceLoader<VertxHttpServerConfigurator> servers = ServiceLoader.load(VertxHttpServerConfigurator.class);
            for (VertxHttpServerConfigurator a : servers)
            {
                server = IGuiceContext.get(a.getClass())
                                      .builder(server);
            }
            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create().setUploadsDirectory("uploads").setDeleteUploadedFilesOnEnd(true));
            ServiceLoader<VertxRouterConfigurator> routes = ServiceLoader.load(VertxRouterConfigurator.class);
            for (VertxRouterConfigurator route : routes)
            {
                router = IGuiceContext.get(route.getClass()).builder(router);
            }
            IJsonRepresentation.configureObjectMapper(DatabindCodec.mapper());

            server = server.requestHandler(router);
            if(Boolean.parseBoolean(Environment.getProperty("HTTP_ENABLED","true")))
            {
                server.listen(Integer.parseInt(Environment.getProperty("HTTP_PORT", "8080")));
            }
            if(Boolean.parseBoolean(Environment.getProperty("HTTPS_ENABLED","true")))
            {

            }
            return true;
        }, getExecutorService()));
    }

    public Vertx getVertx()
    {
        return vertx;
    }

    @Override
    public void onDestroy()
    {
        vertx.close();
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 500;
    }
}
