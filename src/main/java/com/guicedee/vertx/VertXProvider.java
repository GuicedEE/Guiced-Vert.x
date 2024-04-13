package com.guicedee.vertx;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.vertx.core.Vertx;

public class VertXProvider implements Provider<Vertx>
{
    @Inject
    VertXPostStartup postStartup;

    @Override
    public Vertx get()
    {
        return postStartup.getVertx();
    }
}
