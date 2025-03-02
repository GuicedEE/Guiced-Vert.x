package com.guicedee.vertx;

import com.guicedee.guicedinjection.interfaces.IGuicePostStartup;
import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.vertx.spi.VerticleBuilder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Getter
@Singleton
@Log4j2
public class VertXPostStartup implements IGuicePostStartup<VertXPostStartup>, IGuicePreDestroy<VertXPostStartup>
{

    @Inject
    private Vertx vertx;

    @Inject
    private VerticleBuilder verticleBuilder;

    @Override
    public List<CompletableFuture<Boolean>> postLoad()
    {
        Promise<Boolean> promise = Promise.promise();
        vertx.executeBlocking(() -> {
            log.info("Vert.x Post Startup Complete. Checking for verticles");
            var verticlePackages = verticleBuilder.findVerticles();
            if (!verticlePackages.isEmpty() && verticlePackages.keySet().stream().noneMatch(String::isEmpty))
            {
                log.info("Found Verticles. Deploying [{}] verticles...", verticlePackages.size());
                List<Future<?>> futures = new ArrayList<>();
                verticlePackages.forEach((key, value) -> {
                    log.info("Deploying Verticle: {} - {}", key, value.getClass().getSimpleName());
                    futures.add(vertx.deployVerticle(value));
                });
                Future.all(futures).onComplete(ar -> {
                            promise.complete();
                        })
                        .onFailure(promise::fail);
            } else
            {
                promise.complete();
            }
            return true;
        }, false);
        return List.of(promise.future().toCompletionStage().toCompletableFuture());
    }

    @Override
    public void onDestroy()
    {
        vertx.close();
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 50;
    }
}
