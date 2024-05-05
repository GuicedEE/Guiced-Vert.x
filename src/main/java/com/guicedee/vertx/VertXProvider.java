package com.guicedee.vertx;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.vertx.core.Vertx;
import jakarta.inject.Singleton;

@Singleton
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
