package com.guicedee.vertx;

import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.guicedinjection.interfaces.IGuicePreStartup;
import com.guicedee.vertx.spi.VertXPreStartup;
import com.guicedee.vertx.spi.VerticleBuilder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

@Getter
@Singleton
@Log4j2
public class VertXVerticalPreStartup implements IGuicePreStartup<VertXVerticalPreStartup>, IGuicePreDestroy<VertXVerticalPreStartup>
{
    private final VerticleBuilder verticleBuilder = new VerticleBuilder();

    @Override
    public void onDestroy()
    {
        VertXPreStartup.getVertx().close();
    }

    @Override
    public List<Future<Boolean>> onStartup()
    {
        Promise<Boolean> promise = Promise.promise();
        VertXPreStartup.getVertx().executeBlocking(() -> {
            log.info("Vert.x Post Startup Complete. Checking for verticles");
            var verticlePackages = verticleBuilder.findVerticles();
            if (!verticlePackages.isEmpty() && verticlePackages.keySet().stream().noneMatch(String::isEmpty))
            {
                log.info("Found Verticles. Deploying [{}] verticles...", verticlePackages.size());
                List<Future<?>> futures = new ArrayList<>();
                verticlePackages.forEach((key, value) -> {
                    log.info("Deploying Verticle: {} - {}", key, value.getClass().getSimpleName());
                    futures.add(VertXPreStartup.getVertx().deployVerticle(value));
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

        return List.of(promise.future());
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 51;
    }
}
