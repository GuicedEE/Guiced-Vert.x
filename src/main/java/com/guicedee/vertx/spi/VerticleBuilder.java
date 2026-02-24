package com.guicedee.vertx.spi;

import com.google.common.base.Strings;
import com.guicedee.client.IGuiceContext;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.PackageInfo;
import io.vertx.core.*;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Discovers {@link Verticle} annotations, builds verticle instances, and
 * deploys them with per-annotation {@link DeploymentOptions}.
 * <p>
 * Also maintains the package-to-verticle map and deployment futures so
 * other components can resolve verticles by package prefix.
 */
@Log4j2
public class VerticleBuilder
{
    @Getter
    private static final Map<String, io.vertx.core.Verticle> verticlePackages = new TreeMap<>();
    @Getter
    private static final Map<String, Future<?>> verticleFutures = new TreeMap<>();
    @Getter
    private static final List<String> annotatedPrefixes = new ArrayList<>();
    @Getter
    private static final Map<String, Verticle> verticleAnnotations = new TreeMap<>();


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

        // Also discover package-level @Verticle annotations from package-info.java files
        Map<String, Verticle> packageLevelVerticles = new LinkedHashMap<>();
        for (PackageInfo packageInfo : scanResult.getPackageInfo())
        {
            AnnotationInfo annotationInfo = packageInfo.getAnnotationInfo(Verticle.class.getName());
            if (annotationInfo != null)
            {
                Verticle reconstructed = reconstructVerticleAnnotation(annotationInfo);
                packageLevelVerticles.put(packageInfo.getName(), reconstructed);
                log.info("Found package-level Verticle: {} - workerPoolName={}", packageInfo.getName(), reconstructed.workerPoolName());
            }
        }

        Map<ClassInfo, Boolean> classLoaded = new HashMap<>();
        if (foundVerticleClasses.isEmpty() && packageLevelVerticles.isEmpty())
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
                    // Ensure the verticle deployment completes even if startups are synchronous
                    // or do not explicitly complete the promise.
                    if (!startPromise.future().isComplete())
                    {
                        startPromise.tryComplete();
                    }
                    //super.start(startPromise);
                }
            });
            // Deploy after map fully built (single global verticle)
            map.forEach((key, value) -> {
                log.info("Deploying Global Verticle: {} - global-worker-pool", key);
                var verticalFuture = VertXPreStartup.getVertx().deployVerticle(value, new DeploymentOptions());
                verticleFutures.put(key, verticalFuture);
            });
        }
        else
        {
            // Collect all annotated verticle package prefixes (including capabilities)
            List<String> allAnnotatedPackages = new ArrayList<>();

            for (ClassInfo classInfo : foundVerticleClasses)
            {
                var annotation = classInfo.loadClass().getDeclaredAnnotation(Verticle.class);
                log.info("Found Verticle: {} - {}", classInfo.getPackageName(), classInfo.getSimpleName());
                List<String> packageNames = getAppliedPackageNames(classInfo, annotation);
                allAnnotatedPackages.addAll(packageNames);
                verticleAnnotations.put(classInfo.getPackageName(), annotation);

                map.put(classInfo.getPackageName(), new AbstractVerticle()
                {
                    @Override
                    public void start(Promise<Void> startPromise) throws Exception
                    {
                        // Name the current thread after the worker pool for observability
                        String poolName = annotation.workerPoolName();
                        if (poolName != null && !poolName.isEmpty()) {
                            Thread.currentThread().setName(poolName + "-startup");
                        }

                        if(!classLoaded.containsKey(classInfo))
                        {
                            classLoaded.put(classInfo, true);
                        }else {
                            return;
                        }
                        @SuppressWarnings("rawtypes")
                        ServiceLoader<VerticleStartup> startups = ServiceLoader.load(VerticleStartup.class);
                        startups.stream().map(ServiceLoader.Provider::get)
                                .filter(a ->
                                        // Allow startups in the assigned package/capabilities
                                        packageNames.stream().anyMatch(pkg -> a.getClass().getPackage().getName().startsWith(pkg))
                                                // Always allow core vertx SPI startups
                                                || a.getClass().getPackage().getName().startsWith("com.guicedee.vertx.spi")
                                )
                                .forEachOrdered(entry -> {
                                    //noinspection unchecked
                                    entry.start(startPromise, vertx, this, classInfo.getPackageName());
                                });
                        // Ensure deployment completes even if startups don't complete the promise
                        if (!startPromise.future().isComplete())
                        {
                            startPromise.tryComplete();
                        }
                        //super.start(startPromise);
                    }
                });
            }

            // Process package-level @Verticle annotations (from package-info.java files)
            Set<String> packageLoadedKeys = new HashSet<>();
            for (var pkgEntry : packageLevelVerticles.entrySet())
            {
                String pkgName = pkgEntry.getKey();
                Verticle annotation = pkgEntry.getValue();

                // Skip if a class-level verticle already claimed this package
                if (map.containsKey(pkgName))
                {
                    log.debug("Skipping package-level Verticle for {} — already claimed by class-level annotation", pkgName);
                    continue;
                }

                List<String> packageNames = getAppliedPackageNames(pkgName, annotation);
                allAnnotatedPackages.addAll(packageNames);
                verticleAnnotations.put(pkgName, annotation);

                map.put(pkgName, new AbstractVerticle()
                {
                    @Override
                    public void start(Promise<Void> startPromise) throws Exception
                    {
                        // Name the current thread after the worker pool for observability
                        String poolName = annotation.workerPoolName();
                        if (poolName != null && !poolName.isEmpty()) {
                            Thread.currentThread().setName(poolName + "-startup");
                        }

                        if (packageLoadedKeys.contains(pkgName))
                        {
                            return;
                        }
                        packageLoadedKeys.add(pkgName);

                        @SuppressWarnings("rawtypes")
                        ServiceLoader<VerticleStartup> startups = ServiceLoader.load(VerticleStartup.class);
                        startups.stream().map(ServiceLoader.Provider::get)
                                .filter(a ->
                                        packageNames.stream().anyMatch(pkg -> a.getClass().getPackage().getName().startsWith(pkg))
                                                || a.getClass().getPackage().getName().startsWith("com.guicedee.vertx.spi")
                                )
                                .forEachOrdered(entry -> {
                                    //noinspection unchecked
                                    entry.start(startPromise, vertx, this, pkgName);
                                });
                        if (!startPromise.future().isComplete())
                        {
                            startPromise.tryComplete();
                        }
                    }
                });
            }

            // Add a default verticle for everything NOT covered by annotated packages
            // Build an immutable snapshot of prefixes to test against
            final List<String> prefixes = List.copyOf(allAnnotatedPackages);
            map.put("", new AbstractVerticle()
            {
                @Override
                public void start(Promise<Void> startPromise) throws Exception
                {
                    @SuppressWarnings("rawtypes")
                    ServiceLoader<VerticleStartup> startups = ServiceLoader.load(VerticleStartup.class);
                    startups.stream().map(ServiceLoader.Provider::get)
                            // Only include startups whose package does NOT start with any annotated prefix
                            .filter(a -> prefixes.stream().noneMatch(pkg -> !pkg.isEmpty() && a.getClass().getPackage().getName().startsWith(pkg)))
                            .forEachOrdered(entry -> {
                                //noinspection unchecked
                                entry.start(startPromise, vertx, this, "");
                            });
                    // Ensure deployment completes even if startups are synchronous
                    if (!startPromise.future().isComplete())
                    {
                        startPromise.tryComplete();
                    }
                }
            });

            // Deploy all prepared verticles once after map is complete
            map.forEach((key, value) -> {
                // Determine deployment options: for specific verticles, use their annotation; default uses defaults
                DeploymentOptions opts;
                if (key.isEmpty()) {
                    opts = new DeploymentOptions();
                    log.info("Deploying Default Verticle for non-annotated packages");
                } else {
                    // Find class annotation again by key, then fall back to package-level annotation
                    ClassInfo ci = foundVerticleClasses.stream()
                            .filter(c -> c.getPackageName().equals(key))
                            .findFirst()
                            .orElse(null);
                    Verticle ann = ci != null ? ci.loadClass().getDeclaredAnnotation(Verticle.class) : null;
                    if (ann == null)
                    {
                        // Check package-level annotations
                        ann = packageLevelVerticles.get(key);
                    }
                    opts = ann != null ? toDeploymentOptions(ann) : new DeploymentOptions();
                    log.info("Deploying Verticle: {} - workerPool={}", key,
                            ann != null ? ann.workerPoolName() : "default");
                }
                var verticalFuture = VertXPreStartup.getVertx().deployVerticle(value, opts);
                verticleFutures.put(key, verticalFuture);
            });
        }
        // Expose annotated prefixes globally for other components (like consumer registration filtering)
        annotatedPrefixes.clear();
        annotatedPrefixes.addAll(map.keySet());
        verticlePackages.putAll(map);
        return map;
    }

    /**
     * Resolves the {@link Verticle} annotation for the given class based on its package name.
     * The most specific (longest) matching package prefix is returned.
     *
     * @param clazz The class to resolve a Verticle annotation for
     * @return An Optional containing the matching Verticle annotation, or empty if none matches
     */
    public static Optional<Verticle> getVerticleAnnotation(Class<?> clazz)
    {
        String packageName = clazz.getPackageName();
        return verticleAnnotations.entrySet().stream()
                .filter(entry -> !entry.getKey().isEmpty() && packageName.startsWith(entry.getKey()))
                .max(java.util.Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue);
    }

    private List<String> getAppliedPackageNames(ClassInfo classInfo, Verticle annotation)
    {
        return getAppliedPackageNames(classInfo.getPackageName(), annotation);
    }

    private List<String> getAppliedPackageNames(String packageName, Verticle annotation)
    {
        List<String> packageNames = new ArrayList<>();
        packageNames.add(packageName);
        if (annotation.capabilities().length == 0)
        {
            for (Verticle.Capabilities value : Verticle.Capabilities.values())
            {
                packageNames.add(value.getPackageName());
            }
        }
        else
        {
            for (Verticle.Capabilities capability : annotation.capabilities())
            {
                packageNames.add(capability.getPackageName());
            }
        }
        return packageNames;
    }

    /**
     * Reconstructs a {@link Verticle} annotation instance from ClassGraph's {@link AnnotationInfo}.
     * This is needed because package-level annotations cannot be retrieved via {@code Class.getDeclaredAnnotation()},
     * so we must read the annotation parameter values from ClassGraph's scan results.
     */
    private Verticle reconstructVerticleAnnotation(AnnotationInfo annotationInfo)
    {
        var params = annotationInfo.getParameterValues();
        return new Verticle()
        {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType()
            {
                return Verticle.class;
            }

            @Override
            public ThreadingModel threadingModel()
            {
                Object val = params.getValue("threadingModel");
                if (val != null)
                {
                    try { return ThreadingModel.valueOf(val.toString()); }
                    catch (IllegalArgumentException ignored) {}
                }
                return ThreadingModel.EVENT_LOOP;
            }

            @Override
            public int defaultInstances()
            {
                Object val = params.getValue("defaultInstances");
                return val != null ? ((Number) val).intValue() : 1;
            }

            @Override
            public boolean ha()
            {
                Object val = params.getValue("ha");
                return val != null && (Boolean) val;
            }

            @Override
            public String workerPoolName()
            {
                Object val = params.getValue("workerPoolName");
                return val != null ? val.toString() : "";
            }

            @Override
            public int workerPoolSize()
            {
                Object val = params.getValue("workerPoolSize");
                return val != null ? ((Number) val).intValue() : 20;
            }

            @Override
            public long maxWorkerExecuteTime()
            {
                Object val = params.getValue("maxWorkerExecuteTime");
                return val != null ? ((Number) val).longValue() : 2;
            }

            @Override
            public TimeUnit maxWorkerExecuteTimeUnit()
            {
                Object val = params.getValue("maxWorkerExecuteTimeUnit");
                if (val != null)
                {
                    try { return TimeUnit.valueOf(val.toString()); }
                    catch (IllegalArgumentException ignored) {}
                }
                return TimeUnit.MINUTES;
            }

            @Override
            public Capabilities[] capabilities()
            {
                Object val = params.getValue("capabilities");
                if (val instanceof Object[] arr)
                {
                    Capabilities[] caps = new Capabilities[arr.length];
                    for (int i = 0; i < arr.length; i++)
                    {
                        caps[i] = Capabilities.valueOf(arr[i].toString());
                    }
                    return caps;
                }
                return new Capabilities[0];
            }
        };
    }

    private DeploymentOptions toDeploymentOptions(Verticle vertical)
    {
        Verticle wrapped = new Verticle()
        {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType()
            {
                return Verticle.class;
            }

            @Override
            public ThreadingModel threadingModel()
            {
                String threadingStr = com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_VERTICLE_THREADING_MODEL", vertical.threadingModel().name());
                try
                {
                    return ThreadingModel.valueOf(threadingStr);
                }
                catch (IllegalArgumentException e)
                {
                    return vertical.threadingModel();
                }
            }

            @Override
            public int defaultInstances()
            {
                return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_VERTICLE_DEFAULT_INSTANCES", String.valueOf(vertical.defaultInstances())));
            }

            @Override
            public boolean ha()
            {
                return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_VERTICLE_HA", String.valueOf(vertical.ha())));
            }

            @Override
            public String workerPoolName()
            {
                return com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_VERTICLE_WORKER_POOL_NAME", vertical.workerPoolName());
            }

            @Override
            public int workerPoolSize()
            {
                return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_VERTICLE_WORKER_POOL_SIZE", String.valueOf(vertical.workerPoolSize())));
            }

            @Override
            public long maxWorkerExecuteTime()
            {
                return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_VERTICLE_MAX_WORKER_EXECUTE_TIME", String.valueOf(vertical.maxWorkerExecuteTime())));
            }

            @Override
            public TimeUnit maxWorkerExecuteTimeUnit()
            {
                String unitStr = com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_VERTICLE_MAX_WORKER_EXECUTE_TIME_UNIT", vertical.maxWorkerExecuteTimeUnit().name());
                try
                {
                    return TimeUnit.valueOf(unitStr);
                }
                catch (IllegalArgumentException e)
                {
                    return vertical.maxWorkerExecuteTimeUnit();
                }
            }

            @Override
            public Capabilities[] capabilities()
            {
                return vertical.capabilities();
            }
        };

        DeploymentOptions d = new DeploymentOptions();
        if (wrapped.ha())
        {
            d.setHa(true);
        }
        if (wrapped.threadingModel() != ThreadingModel.EVENT_LOOP)
        {
            d.setThreadingModel(wrapped.threadingModel());
        }
        if (wrapped.defaultInstances() != 1)
        {
            d.setInstances(wrapped.defaultInstances());
        }
        if (!Strings.isNullOrEmpty(wrapped.workerPoolName()))
        {
            d.setWorkerPoolName(wrapped.workerPoolName());
        }
        if (wrapped.workerPoolSize() != 20)
        {
            d.setWorkerPoolSize(wrapped.workerPoolSize());
        }
        if (wrapped.maxWorkerExecuteTime() != 2)
        {
            d.setMaxWorkerExecuteTime(wrapped.maxWorkerExecuteTime());
        }
        if (wrapped.maxWorkerExecuteTimeUnit() != TimeUnit.MINUTES)
        {
            d.setMaxWorkerExecuteTimeUnit(wrapped.maxWorkerExecuteTimeUnit());
        }
        return d;
    }

}
