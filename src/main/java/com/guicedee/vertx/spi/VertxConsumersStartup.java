package com.guicedee.vertx.spi;

import com.guicedee.vertx.VertxEventDefinition;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import lombok.extern.log4j.Log4j2;

/**
 * Verticle startup hook that registers event consumers scoped to the
 * verticle's assigned package.
 */
@Log4j2
public class VertxConsumersStartup implements VerticleStartup<VertxConsumersStartup>
{
    @Override
    public void start(Promise<Void> startPromise, Vertx vertx, AbstractVerticle verticle, String assignedPackage)
    {
        try {
            // Build inclusion predicate matching current assigned package vs. annotated verticles
            var excludedPrefixes = VerticleBuilder.getAnnotatedPrefixes();
            java.util.function.Predicate<String> includeByPackage;
            if (assignedPackage != null && !assignedPackage.isEmpty()) {
                includeByPackage = pkg -> pkg != null && pkg.startsWith(assignedPackage);
            } else {
                java.util.List<String> excludes = excludedPrefixes == null ? java.util.List.of() : excludedPrefixes;
                includeByPackage = pkg -> pkg != null && excludes.stream().noneMatch(p -> !p.isEmpty() && pkg.startsWith(p));
            }

            var definitions = VertxEventRegistry.getEventConsumerDefinitions();
            var classMap = VertxEventRegistry.getEventConsumerClass();
            var methodMap = VertxEventRegistry.getEventConsumerMethods();
            var methodClassMap = VertxEventRegistry.getEventConsumerMethodClasses();

            // Deploy a dedicated verticle per address (per @VertxEventDefinition)
            definitions.forEach((address, def) -> {
                try {
                    if (!def.options().autobind()) {
                        return; // skip manual bindings
                    }

                    boolean isClassBased = classMap.containsKey(address);
                    boolean isMethodBased = methodMap.containsKey(address);

                    Class<?> targetClass;
                    java.lang.reflect.Method targetMethod = null;

                    if (isClassBased) {
                        targetClass = classMap.get(address);
                        if (!includeByPackage.test(targetClass.getPackageName())) {
                            return;
                        }
                        try {
                            targetMethod = targetClass.getMethod("consume", Message.class);
                        } catch (NoSuchMethodException nsme) {
                            log.error("No consume(Message) method found for class {} at address {}", targetClass.getName(), address);
                            return;
                        }
                    } else if (isMethodBased) {
                        targetMethod = methodMap.get(address);
                        targetClass = methodClassMap.get(address);
                        if (targetClass == null) {
                            log.error("No method class found for address {}", address);
                            return;
                        }
                        if (!includeByPackage.test(targetClass.getPackageName())) {
                            return;
                        }
                    } else {
                        // Unknown definition type
                        log.warn("No consumer target found for address {}", address);
                        return;
                    }

                    int instances = Math.max(1, def.options().instances() > 0 ? def.options().instances() : def.options().consumerCount());

                    DeploymentOptions options = new DeploymentOptions();
                    if (def.options().worker()) {
                        String pool = def.options().workerPool();
                        int poolSize = def.options().workerPoolSize();
                        if (pool != null && !pool.isEmpty()) {
                            options.setWorkerPoolName(pool);
                        }
                        if (poolSize > 0) {
                            options.setWorkerPoolSize(poolSize);
                        }
                    }
                    options.setInstances(instances);

                    var consumerVerticle = new EventConsumerVerticle(address, def, targetMethod, targetClass);
                    vertx.deployVerticle(consumerVerticle, options).onFailure(t ->
                                    log.error("Failed to deploy consumer verticle for {}: {}", address, t.getMessage(), t)
                            )
                            .onSuccess(id -> log.debug("Deployed consumer verticle [{}] for address {} (instances={})", id, address, instances));
                } catch (Throwable t) {
                    log.error("Error while deploying consumer verticle for {}", address, t);
                }
            });

            log.debug("VertxConsumersStartup completed per-address deployments for assignedPackage='{}'", assignedPackage);
        } catch (Throwable t) {
            log.error("Failed to deploy per-address consumer verticles for assignedPackage='{}'", assignedPackage, t);
            // Do not fail the verticle start promise here to avoid cascading failures.
        }
    }
}
