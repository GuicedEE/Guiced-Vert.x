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
import java.util.concurrent.ConcurrentHashMap;
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
     * Tracks VerticleStartup classes that have already been dispatched to prevent
     * duplicate registrations when nested @Verticle packages overlap.
     */
    private static final Set<String> dispatchedStartups = ConcurrentHashMap.newKeySet();


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

            // --- Merge class-level and package-level @Verticle entries into a unified map ---
            // Key = package name, Value = annotation
            Map<String, Verticle> mergedVerticles = new LinkedHashMap<>();

            for (ClassInfo classInfo : foundVerticleClasses)
            {
                var annotation = classInfo.loadClass().getDeclaredAnnotation(Verticle.class);
                log.info("Found Verticle (class-level): {} - {}", classInfo.getPackageName(), classInfo.getSimpleName());
                mergedVerticles.put(classInfo.getPackageName(), annotation);
            }

            for (var pkgEntry : packageLevelVerticles.entrySet())
            {
                String pkgName = pkgEntry.getKey();
                if (mergedVerticles.containsKey(pkgName))
                {
                    log.debug("Skipping package-level Verticle for {} — already claimed by class-level annotation", pkgName);
                    continue;
                }
                log.info("Found Verticle (package-level): {}", pkgName);
                mergedVerticles.put(pkgName, pkgEntry.getValue());
            }

            // --- Sort bottom-up: deepest (most specific) packages first ---
            // This ensures nested @Verticle packages are processed before their parents,
            // so the most specific verticle claims its VerticleStartup implementations first.
            List<Map.Entry<String, Verticle>> sortedEntries = new ArrayList<>(mergedVerticles.entrySet());
            sortedEntries.sort((a, b) -> {
                // Count dots as a proxy for package depth; more dots = deeper
                long depthA = a.getKey().chars().filter(c -> c == '.').count();
                long depthB = b.getKey().chars().filter(c -> c == '.').count();
                return Long.compare(depthB, depthA); // descending (deepest first)
            });

            // Collect all annotated package keys for building the nested-exclusion filter
            List<String> allVerticleKeys = sortedEntries.stream().map(Map.Entry::getKey).toList();

            // Track which verticle entries have already started (guard against double-deploy)
            Set<String> startedVerticleKeys = ConcurrentHashMap.newKeySet();

            for (var entry : sortedEntries)
            {
                String verticlePkgName = entry.getKey();
                Verticle annotation = entry.getValue();

                List<String> packageNames = getAppliedPackageNames(verticlePkgName, annotation);
                allAnnotatedPackages.addAll(packageNames);
                verticleAnnotations.put(verticlePkgName, annotation);

                // Build the set of more-specific (nested) verticle prefixes that should be excluded
                // from this verticle's startup filter. A prefix P is "nested" if it starts with
                // this verticle's package AND is longer (more specific).
                final List<String> nestedVerticlePrefixes = allVerticleKeys.stream()
                        .filter(k -> !k.equals(verticlePkgName)
                                && !k.isEmpty()
                                && k.startsWith(verticlePkgName))
                        .toList();

                map.put(verticlePkgName, new AbstractVerticle()
                {
                    @Override
                    public void start(Promise<Void> startPromise) throws Exception
                    {
                        // Name the current thread after the worker pool for observability
                        String poolName = annotation.workerPoolName();
                        if (poolName != null && !poolName.isEmpty())
                        {
                            Thread.currentThread().setName(poolName + "-startup");
                        }

                        // Guard against double-start
                        if (!startedVerticleKeys.add(verticlePkgName))
                        {
                            return;
                        }

                        @SuppressWarnings("rawtypes")
                        ServiceLoader<VerticleStartup> startups = ServiceLoader.load(VerticleStartup.class);
                        startups.stream().map(ServiceLoader.Provider::get)
                                .filter(a -> {
                                    String startupPkg = a.getClass().getPackage().getName();

                                    // Always allow core vertx SPI startups
                                    if (startupPkg.startsWith("com.guicedee.vertx.spi"))
                                    {
                                        return true;
                                    }

                                    // Must match this verticle's package or its capabilities
                                    boolean matchesThisVerticle = packageNames.stream()
                                            .anyMatch(pkg -> startupPkg.startsWith(pkg));
                                    if (!matchesThisVerticle)
                                    {
                                        return false;
                                    }

                                    // Exclude if a more-specific nested @Verticle claims this startup's package
                                    boolean claimedByNested = nestedVerticlePrefixes.stream()
                                            .anyMatch(startupPkg::startsWith);
                                    return !claimedByNested;
                                })
                                // Exclude any startup that was already dispatched by another verticle
                                .filter(a -> dispatchedStartups.add(a.getClass().getName()))
                                .forEachOrdered(startupEntry -> {
                                    //noinspection unchecked
                                    startupEntry.start(startPromise, vertx, this, verticlePkgName);
                                });

                        // Ensure deployment completes even if startups don't complete the promise
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
                            // Exclude any startup already dispatched by a specific verticle
                            .filter(a -> dispatchedStartups.add(a.getClass().getName()))
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

            // Deploy all prepared verticles once after map is complete.
            // Deploy in the same bottom-up order: deepest verticles first, default ("") last.
            List<String> deployOrder = new ArrayList<>(sortedEntries.stream().map(Map.Entry::getKey).toList());
            deployOrder.add(""); // default verticle last
            for (String key : deployOrder)
            {
                io.vertx.core.Verticle value = map.get(key);
                if (value == null) continue;

                DeploymentOptions opts;
                if (key.isEmpty())
                {
                    opts = new DeploymentOptions();
                    log.info("Deploying Default Verticle for non-annotated packages");
                }
                else
                {
                    Verticle ann = verticleAnnotations.get(key);
                    opts = ann != null ? toDeploymentOptions(ann) : new DeploymentOptions();
                    log.info("Deploying Verticle: {} - workerPool={}", key,
                            ann != null ? ann.workerPoolName() : "default");
                }
                var verticalFuture = VertXPreStartup.getVertx().deployVerticle(value, opts);
                verticleFutures.put(key, verticalFuture);
            }
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
