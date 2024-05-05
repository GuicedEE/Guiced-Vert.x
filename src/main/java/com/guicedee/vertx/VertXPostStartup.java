package com.guicedee.vertx;

import com.guicedee.client.Environment;
import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.guicedinjection.interfaces.IGuicePreStartup;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import com.guicedee.vertx.spi.VertxConfigurator;
import com.guicedee.vertx.spi.VertxHttpServerConfigurator;
import com.guicedee.vertx.spi.VertxHttpServerOptionsConfigurator;
import com.guicedee.vertx.spi.VertxRouterConfigurator;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.Router;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.ServiceLoader;

@Singleton
@Getter
public class VertXPostStartup implements IGuicePreStartup<VertXPostStartup>, IGuicePreDestroy<VertXPostStartup>
{
    private static Vertx vertx;

    @Override
    public void onStartup()
    {
        if (vertx == null)
        {
            VertxBuilder builder = Vertx.builder();
            ServiceLoader<VertxConfigurator> load = ServiceLoader.load(VertxConfigurator.class);
            for (VertxConfigurator a : load)
            {
                builder = a.builder(builder);
            }
            vertx = builder.build();
            HttpServerOptions serverOptions = new HttpServerOptions();
            ServiceLoader<VertxHttpServerOptionsConfigurator> options = ServiceLoader.load(VertxHttpServerOptionsConfigurator.class);
            for (VertxHttpServerOptionsConfigurator option : options)
            {
                serverOptions = option.builder(serverOptions);
            }
            HttpServer server = vertx.createHttpServer(serverOptions);
            ServiceLoader<VertxHttpServerConfigurator> servers = ServiceLoader.load(VertxHttpServerConfigurator.class);
            for (VertxHttpServerConfigurator a : servers)
            {
                server = a.builder(server);
            }
            Router router = Router.router(vertx);
            ServiceLoader<VertxRouterConfigurator> routes = ServiceLoader.load(VertxRouterConfigurator.class);
            for (VertxRouterConfigurator route : routes)
            {
                router = route.builder(router);
            }
            IJsonRepresentation.configureObjectMapper(DatabindCodec.mapper());

            server = server.requestHandler(router);
            server .listen(Integer.parseInt(Environment.getProperty("HTTP_PORT", "8080")));
        }
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
