package com.guicedee.vertx;

import com.google.inject.PrivateModule;
import com.guicedee.guicedinjection.interfaces.IGuiceModule;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;

public class VertXModule extends PrivateModule implements IGuiceModule<VertXModule>
{
    @Override
    protected void configure()
    {
        bind(Vertx.class).toProvider(VertXProvider.class);
        expose(Vertx.class);
    }
}
