package com.guicedee.vertx;

import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.guicedinjection.interfaces.IGuicePreStartup;
import com.guicedee.vertx.spi.VertxConfigurator;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
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
            VertxBuilder builder = Vertx.builder();
            ServiceLoader<VertxConfigurator> load = ServiceLoader.load(VertxConfigurator.class);
            for (VertxConfigurator a : load)
            {
                builder = a.builder(builder);
            }
            vertx = builder.build();
        }
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
