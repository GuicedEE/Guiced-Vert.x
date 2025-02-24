package com.guicedee.vertx;

import com.google.common.base.Strings;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.spi.Verticle;
import com.guicedee.vertx.spi.VerticleStartup;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.vertx.core.*;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Log4j2
public class VerticleBuilder
{
    @Inject
    private Vertx vertx;

    private Map<String, io.vertx.core.Verticle> verticles;


    private Map<String, io.vertx.core.Verticle> findVerticles()
    {
        Map<String, io.vertx.core.Verticle> map = new HashMap<>();
        var scanResult = IGuiceContext.instance().getScanResult();
        ClassInfoList foundVerticleClasses = scanResult.getClassesWithAnnotation(Verticle.class);
        log.info("Found Verticles: " + foundVerticleClasses.size());
        if (foundVerticleClasses.isEmpty())
        {
            //use and configure everything
            Set<String> packageNames = Set.of("");
            map.put("", new AbstractVerticle()
            {
                @Override
                public void start(Promise<Void> startPromise) throws Exception
                {
                    ServiceLoader<VerticleStartup> startups = ServiceLoader.load(VerticleStartup.class);
                    startups.stream().map(ServiceLoader.Provider::get)
                            .filter(a -> packageNames.stream()
                                    .anyMatch(pkg -> a.getClass().getPackage().getName().startsWith(pkg)))
                            .forEachOrdered(entry -> {
                                entry.start(startPromise, vertx, this, "");
                            });
                    super.start(startPromise);
                }
            });

            map.forEach((key, value) -> {
                log.info("Deploying Everything Verticle: " + key + " - " + "global-worker-pool");
                vertx.deployVerticle(value, new DeploymentOptions());
            });


        }
        for (ClassInfo classInfo : foundVerticleClasses)
        {
            var annotation = classInfo.loadClass().getDeclaredAnnotation(Verticle.class);
            log.info("Found Verticle: " + classInfo.getPackageName() + " - " + classInfo.getSimpleName());
            List<String> packageNames = new ArrayList<>();
            packageNames.add(classInfo.getPackageName());
            if (annotation.capabilities().length == 0)
            {
                for (Verticle.Capabilities value : Verticle.Capabilities.values())
                {
                    packageNames.add(value.getPackageName());
                }
            }else {
                for (Verticle.Capabilities capability : annotation.capabilities())
                {
                    packageNames.add(capability.getPackageName());
                }
            }

            map.put(classInfo.getPackageName(), new AbstractVerticle()
            {
                @Override
                public void start(Promise<Void> startPromise) throws Exception
                {
                    ServiceLoader<VerticleStartup> startups = ServiceLoader.load(VerticleStartup.class);
                    startups.stream().map(ServiceLoader.Provider::get)
                            .filter(a -> packageNames.stream()
                                    .anyMatch(pkg -> a.getClass().getPackage().getName().startsWith(pkg)))
                            .forEachOrdered(entry -> {
                                entry.start(startPromise, vertx, this, classInfo.getPackageName());
                            });
                    super.start(startPromise);
                }
            });

            map.forEach((key, value) -> {
                log.info("Deploying Verticle: " + key + " - " + annotation.workerPoolName());
                vertx.deployVerticle(value, toDeploymentOptions(annotation));
            });
        }
        return map;
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
