package com.guicedee.vertx;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.guicedee.guicedinjection.interfaces.IGuicePostStartup;
import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.vertx.spi.VertXPreStartup;
import com.guicedee.vertx.spi.VerticleBuilder;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Getter
@Singleton
@Log4j2
public class VertXPostStartup implements IGuicePostStartup<VertXPostStartup>, IGuicePreDestroy<VertXPostStartup>
{

    @Inject
    private Vertx vertx;

    @Inject
    private VerticleBuilder verticleBuilder;

    @Inject
    private GuicedVertxContextPropagationHandler propagationHandler;

    @Override
    public List<Future<Boolean>> postLoad()
    {
       /* Promise<Boolean> promise = Promise.promise();
        log.info("Vert.x Post Startup Complete. Checking for verticles");
        List<Future<Boolean>> verticles = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();

        var verticlePackages = verticleBuilder.findVerticles();
        log.info("Deploying [{}] verticles...", verticlePackages.size());
        if (!verticlePackages.isEmpty() && verticlePackages.keySet().stream().noneMatch(String::isEmpty))
        {
            verticles.add(vertx.executeBlocking(() -> {
                verticlePackages.forEach((key, value) -> {
                    log.info("Deploying Verticle: {} - {}", key, value.getClass().getSimpleName());
                    futures.add(vertx.deployVerticle(value));
                });
                return true;
            }, false));
        }
        Future.all(futures).onComplete(ar -> {
                    promise.complete();
                })
                .onFailure(promise::fail);
*/
        return List.of(Future.succeededFuture(true));
    }

    @Override
    public void onDestroy()
    {
        if (this.vertx != null)
        {
            vertx.close();
        }
        if (VertXPreStartup.getVertx() != null)
        {
            VertXPreStartup.getVertx().close();
        }
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 50;
    }
}
