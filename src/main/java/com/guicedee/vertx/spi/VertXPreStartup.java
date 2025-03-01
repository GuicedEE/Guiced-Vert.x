package com.guicedee.vertx.spi;

import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.guicedinjection.interfaces.IGuicePreStartup;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.jackson.DatabindCodec;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.ServiceLoader;

@Singleton
@Getter
public class VertXPreStartup implements IGuicePreStartup<VertXPreStartup>, IGuicePreDestroy<VertXPreStartup>
{
    @Getter
    public static Vertx vertx;

    @Override
    public void onStartup()
    {
        if (vertx == null)
        {
            IJsonRepresentation.configureObjectMapper(DatabindCodec.mapper());

            VertxBuilder builder = Vertx.builder();
            ServiceLoader<VertxConfigurator> load = ServiceLoader.load(VertxConfigurator.class);
            builder.with(new VertxOptions()
                    .setBlockedThreadCheckInterval(10000)
                    .setWarningExceptionTime(2000)
                    .setWorkerPoolSize(20)
            );
            for (VertxConfigurator a : load)
            {
                builder = a.builder(builder);
            }
            vertx = builder.build();
        }
    }

    public static Vertx getVertx()
    {
        if (vertx == null)
        {
            new VertXPreStartup().onStartup();
        }
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
