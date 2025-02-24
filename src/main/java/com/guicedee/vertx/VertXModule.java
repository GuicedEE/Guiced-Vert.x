package com.guicedee.vertx;

import com.google.inject.PrivateModule;
import com.guicedee.guicedinjection.interfaces.IGuiceModule;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.core.Vertx;

public class VertXModule extends PrivateModule implements IGuiceModule<VertXModule>
{
    @Override
    protected void configure()
    {
        bind(Vertx.class).toInstance(VertXPreStartup.getVertx());
        expose(Vertx.class);



    }
}
