package com.guicedee.vertx;

import com.guicedee.guicedinjection.interfaces.IGuicePostStartup;
import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.java.Log;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Getter
@Singleton
@Log
public class VertXPostStartup implements IGuicePostStartup<VertXPostStartup>, IGuicePreDestroy<VertXPostStartup> {

    @Inject
    private Vertx vertx;

    @Override
    public List<CompletableFuture<Boolean>> postLoad() {

        return List.of();
    }

    @Override
    public void onDestroy() {
        vertx.close();
    }

}
