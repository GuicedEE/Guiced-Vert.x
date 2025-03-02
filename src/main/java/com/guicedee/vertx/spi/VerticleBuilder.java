package com.guicedee.vertx.spi;

import com.google.common.base.Strings;
import com.guicedee.client.IGuiceContext;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.vertx.core.*;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Log4j2
public class VerticleBuilder
{
    @Getter
    private static final Map<String, io.vertx.core.Verticle> verticlePackages = new TreeMap<>();
    @Getter
    private static final Map<String, Future<?>> verticleFutures = new TreeMap<>();


    /**
     * Finds and prepares the application's Verticles for deployment.
     * This method scans for all classes annotated with the {@code @Verticle} annotation,
     * dynamically initializes and prepares them for deployment within a Vert.x environment.
     * If no Verticles are found, a default "everything" Verticle is deployed.
     *
     * @return A map where the key is a package name and the value is the corresponding Verticle instance.
     */
    public Map<String, io.vertx.core.Verticle> findVerticles()
    {
        if (!verticlePackages.isEmpty())
        {
            return verticlePackages;
        }
        Map<String, io.vertx.core.Verticle> map = new HashMap<>();
        var scanResult = IGuiceContext.instance().getScanResult();
        ClassInfoList foundVerticleClasses = scanResult.getClassesWithAnnotation(Verticle.class);
        if (foundVerticleClasses.isEmpty())
        {
            //use and configure everything
            Set<String> packageNames = Set.of("");
            map.put("", new AbstractVerticle()
            {
                @Override
                public void start(Promise<Void> startPromise) throws Exception
                {
                    @SuppressWarnings("rawtypes")
                    ServiceLoader<VerticleStartup> startups = ServiceLoader.load(VerticleStartup.class);
                    startups.stream().map(ServiceLoader.Provider::get)
                            .filter(a -> packageNames.stream()
                                    .anyMatch(pkg -> a.getClass().getPackage().getName().startsWith(pkg)))
                            .forEachOrdered(entry -> {
                                //noinspection unchecked
                                entry.start(startPromise, vertx, this, "");
                            });
                    super.start(startPromise);
                }
            });

            map.forEach((key, value) -> {
                log.info("Deploying Global Verticle: {} - global-worker-pool", key);
                var verticalFuture = VertXPreStartup.getVertx().deployVerticle(value, new DeploymentOptions());
                verticleFutures.put(key, verticalFuture);
            });
        }
        for (ClassInfo classInfo : foundVerticleClasses)
        {
            var annotation = classInfo.loadClass().getDeclaredAnnotation(Verticle.class);
            log.info("Found Verticle: {} - {}", classInfo.getPackageName(), classInfo.getSimpleName());
            List<String> packageNames = getAppliedPackageNames(classInfo, annotation);

            map.put(classInfo.getPackageName(), new AbstractVerticle()
            {
                @Override
                public void start(Promise<Void> startPromise) throws Exception
                {
                    @SuppressWarnings("rawtypes")
                    ServiceLoader<VerticleStartup> startups = ServiceLoader.load(VerticleStartup.class);
                    startups.stream().map(ServiceLoader.Provider::get)
                            .filter(a -> packageNames.stream()
                                    .anyMatch(pkg -> a.getClass().getPackage().getName().startsWith(pkg)))
                            .forEachOrdered(entry -> {
                                //noinspection unchecked
                                entry.start(startPromise, vertx, this, classInfo.getPackageName());
                            });
                    super.start(startPromise);
                }
            });

            map.forEach((key, value) -> {
                log.info("Deploying Verticle: {} - {}", key, annotation.workerPoolName());
                var verticalFuture = VertXPreStartup.getVertx().deployVerticle(value, toDeploymentOptions(annotation));
                verticleFutures.put(key, verticalFuture);
            });
        }
        verticlePackages.putAll(map);
        return map;
    }

    private List<String> getAppliedPackageNames(ClassInfo classInfo, Verticle annotation)
    {
        List<String> packageNames = new ArrayList<>();
        packageNames.add(classInfo.getPackageName());
        if (annotation.capabilities().length == 0)
        {
            for (Verticle.Capabilities value : Verticle.Capabilities.values())
            {
                packageNames.add(value.getPackageName());
            }
        } else
        {
            for (Verticle.Capabilities capability : annotation.capabilities())
            {
                packageNames.add(capability.getPackageName());
            }
        }
        return packageNames;
    }

    private DeploymentOptions toDeploymentOptions(Verticle vertical)
    {
        DeploymentOptions d = new DeploymentOptions();
        if (vertical.ha())
        {
            d.setHa(true);
        }
        if (vertical.threadingModel() != ThreadingModel.EVENT_LOOP)
        {
            d.setThreadingModel(vertical.threadingModel());
        }
        if (vertical.defaultInstances() != 1)
        {
            d.setInstances(vertical.defaultInstances());
        }
        if (!Strings.isNullOrEmpty(vertical.workerPoolName()))
        {
            d.setWorkerPoolName(vertical.workerPoolName());
        }
        if (vertical.workerPoolSize() != 20)
        {
            d.setWorkerPoolSize(vertical.workerPoolSize());
        }
        if (vertical.maxWorkerExecuteTime() != 2)
        {
            d.setMaxWorkerExecuteTime(vertical.maxWorkerExecuteTime());
        }
        if (vertical.maxWorkerExecuteTimeUnit() != TimeUnit.MINUTES)
        {
            d.setMaxWorkerExecuteTimeUnit(vertical.maxWorkerExecuteTimeUnit());
        }

        return d;
    }

}
