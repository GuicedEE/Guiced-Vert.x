package com.guicedee.vertx.test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.guicedee.client.IGuiceContext;
import com.guicedee.client.services.IDefaultService;
import com.guicedee.client.services.IGuiceConfig;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import com.guicedee.vertx.VertXModule;
import com.guicedee.vertx.spi.VertXPreStartup;
import com.guicedee.vertx.spi.VertxEventRegistry;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.vertx.core.Future;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Lightweight {@link IGuiceContext} implementation for unit tests.
 * <p>
 * Provides a minimal Guice injector and a ClassGraph scan result so that
 * tests can run without pulling in the full {@code com.guicedee:guice-injection}
 * module.
 * <p>
 * On first call to {@link #inject()}, this context will:
 * <ol>
 *   <li>Initialize the ClassGraph scan result</li>
 *   <li>Run {@link VertXPreStartup#onStartup()} to create the Vert.x instance</li>
 *   <li>Create a Guice injector that includes the {@link VertXModule}</li>
 * </ol>
 */
public class TestGuiceContext implements IGuiceContext {

    private static final TestGuiceContext INSTANCE = new TestGuiceContext();

    private final TestGuiceConfig config = new TestGuiceConfig();
    private volatile Injector injector;
    private volatile ScanResult scanResult;
    private volatile boolean buildingInjector;

    public static TestGuiceContext instance() {
        return INSTANCE;
    }

    @Override
    public Future<Void> getLoadingFinished() {
        return Future.succeededFuture();
    }

    @Override
    public Injector inject() {
        if (injector == null) {
            synchronized (this) {
                if (injector == null) {
                    buildingInjector = true;
                    try {
                        // Ensure the scan result is available for event scanning
                        getScanResult();

                        // Boot Vert.x so VertXPreStartup.getVertx() is available
                        VertXPreStartup preStartup = new VertXPreStartup();
                        var futures = preStartup.onStartup();
                        // Wait for all verticle deployments to complete
                        if (futures != null && !futures.isEmpty()) {
                            for (var f : futures) {
                                f.toCompletionStage().toCompletableFuture().join();
                            }
                        }
                        // Allow async EventConsumerVerticle deployments time to register
                        // their consumers on the event bus
                        try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

                        // Directly register any remaining consumers on the event bus
                        // as a fallback for async verticle deployments that may not have completed
                        VertxEventRegistry.registerEventConsumersFiltered("", java.util.List.of());

                        // Collect all modules: user-registered + VertXModule
                        List<Module> allModules = new ArrayList<>(IGuiceContext.modules);
                        allModules.add(new VertXModule());
                        injector = Guice.createInjector(allModules);
                    } finally {
                        buildingInjector = false;
                    }
                }
            }
        }
        return injector;
    }

    @Override
    public IGuiceConfig<?> getConfig() {
        return config;
    }

    @Override
    public void destroy() {
        injector = null;
        if (scanResult != null) {
            scanResult.close();
            scanResult = null;
        }
    }

    @Override
    public ScanResult getScanResult() {
        if (scanResult == null) {
            synchronized (this) {
                if (scanResult == null) {
                    ClassGraph classGraph = new ClassGraph()
                            .enableAllInfo()
                            .acceptPackages("com.guicedee");
                    scanResult = classGraph.scan();
                }
            }
        }
        return scanResult;
    }

    @Override
    public <T extends IDefaultService<T>> Set<T> getLoader(Class<T> loaderType, ServiceLoader<T> serviceLoader) {
        return IGuiceContext.loaderToSet(serviceLoader);
    }

    @Override
    public <T extends IDefaultService<T>> Set<T> getLoader(Class<T> loaderType, boolean dontInject, ServiceLoader<T> serviceLoader) {
        return IGuiceContext.loaderToSetNoInjection(serviceLoader);
    }

    @Override
    public boolean isBuildingInjector() {
        return buildingInjector;
    }

    @Override
    public Set<IGuicePreDestroy> loadPreDestroyServices() {
        return Collections.emptySet();
    }

    @Override
    public Set<IGuicePreStartup> loadPreStartupServices() {
        return Collections.emptySet();
    }
}

