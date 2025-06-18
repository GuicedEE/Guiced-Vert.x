package com.guicedee.vertx;

import com.google.inject.Inject;
import com.guicedee.client.CallScopeProperties;
import com.guicedee.client.CallScoper;
import com.guicedee.client.IGuiceContext;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.spi.VertxThreadFactory;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.TimeUnit;

@Log4j2
public class CallScopeAwareVertxThreadFactory implements VertxThreadFactory {


    @Override
    public void init(VertxBootstrap builder) {
        if (builder.threadFactory() == null) {
            builder.threadFactory(this);
        }
    }

    @Override
    public VertxThread newVertxThread(Runnable target, String name, boolean worker,
                                      long maxExecTime, TimeUnit maxExecTimeUnit) {

        // Capture the current call scope state from the calling thread
        var snapshot = captureCallScopeSnapshot();
        // Wrap the target runnable with scope propagation
        CallScoper scope =  IGuiceContext.get(CallScoper.class);

        Runnable wrappedTarget = () -> {
            boolean scopeEntered = false;
            try {
                // If there was an active scope on the calling thread, restore it
                if (snapshot != null) {
                    scope.enter();
                    scopeEntered = true;
                    restoreCallScopeSnapshot(snapshot);
                }
                // Execute the original task
                target.run();

            } finally {
                // Exit the scope if we entered it
                if (scopeEntered) {
                    try {
                        scope.exit();
                    } catch (Exception e) {
                        // Log the exception but don't let it propagate
                        log.error("Error exiting call scope: {}", e.getMessage(),e);
                    }
                }
            }
        };

        // Create the appropriate thread type
        if (worker) {
            // For worker threads, you might want virtual threads
            return new CallScopeAwareVirtualVertxThread(wrappedTarget, name, worker, maxExecTime, maxExecTimeUnit);
        } else {
            // For event loop threads, use standard VertxThread
            return new VertxThread(wrappedTarget, name, worker, maxExecTime, maxExecTimeUnit);
        }
    }

    /**
     * Captures the current call scope state from the calling thread
     */
    private CallScopeProperties captureCallScopeSnapshot() {
        try {
            // Check if there's an active scope
            if (!isCallScopeActive()) {
                return null;
            }
            var scopeProperties = IGuiceContext.get(CallScopeProperties.class);

            if (scopeProperties != null) {
                var scoper = IGuiceContext.get(CallScoper.class);
                var props = IGuiceContext.get(CallScopeProperties.class);
                return props;
            }
        } catch (Exception e) {
            // Log but don't fail - scope propagation is best effort
            log.error("Failed to capture call scope snapshot: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Restores the call scope state in the new thread
     */
    private void restoreCallScopeSnapshot(CallScopeProperties snapshot) {
        try {
            CallScopeProperties scopeProperties = IGuiceContext.get(CallScopeProperties.class);
            if (scopeProperties != null) {
                // Restore scope values
                // Restore properties
                if (snapshot.getProperties() != null) {
                    scopeProperties.setProperties(snapshot.getProperties());
                    scopeProperties.setSource(snapshot.getSource());
                }
            }
        } catch (Exception e) {
            log.error("Failed to restore call scope snapshot: {}", e.getMessage(),e);
        }
    }

    /**
     * Check if call scope is currently active
     */
    private boolean isCallScopeActive() {
        var scoper = IGuiceContext.get(CallScoper.class);
        return scoper.isStartedScope();
    }

}


/**
 * Virtual thread implementation for worker threads
 */
class CallScopeAwareVirtualVertxThread extends VertxThread {
    private Thread virtualThread;

    public CallScopeAwareVirtualVertxThread(Runnable target, String name, boolean worker,
                                            long maxExecTime, TimeUnit maxExecTimeUnit) {
        super(null, name, worker, maxExecTime, maxExecTimeUnit);

        this.virtualThread = Thread.ofVirtual()
                .name(name)
                .unstarted(() -> {
                    Thread.currentThread().setContextClassLoader(this.getContextClassLoader());
                    target.run();
                });
    }

    @Override
    public void start() {
        virtualThread.start();
    }

    @Override
    public void interrupt() {
        virtualThread.interrupt();
    }

    @Override
    public boolean isInterrupted() {
        return virtualThread.isInterrupted();
    }

}