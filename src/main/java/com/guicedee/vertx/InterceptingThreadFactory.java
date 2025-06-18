package com.guicedee.vertx;

import io.vertx.core.spi.VertxThreadFactory;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.internal.VertxBootstrap;
import java.util.concurrent.TimeUnit;

public class InterceptingThreadFactory implements VertxThreadFactory {
    private final boolean useVirtualThreadsForWorkers;

    public InterceptingThreadFactory() {
        this(true);
    }

    public InterceptingThreadFactory(boolean useVirtualThreadsForWorkers) {
        this.useVirtualThreadsForWorkers = useVirtualThreadsForWorkers;
    }

    @Override
    public void init(VertxBootstrap builder) {
        if (builder.threadFactory() == null) {
            builder.threadFactory(this);
        }
    }

    @Override
    public VertxThread newVertxThread(Runnable target, String name, boolean worker,
                                    long maxExecTime, TimeUnit maxExecTimeUnit) {

        // Wrap the target runnable with pre/post actions
        Runnable wrappedTarget = () -> {
            preThreadAction(name, worker);
            try {
                target.run();
            } finally {
                postThreadAction(name, worker);
            }
        };

        // For worker threads, optionally use virtual threads
        if (worker && useVirtualThreadsForWorkers) {
            return new VirtualVertxThread(wrappedTarget, name, worker, maxExecTime, maxExecTimeUnit);
        }

        // Use standard VertxThread for event loop threads and when virtual threads disabled
        return new VertxThread(wrappedTarget, name, worker, maxExecTime, maxExecTimeUnit);
    }

    private void preThreadAction(String threadName, boolean isWorker) {
        System.out.println("PRE: Starting Vert.x thread: " + threadName +
                          " (worker: " + isWorker + ")");
        // Add your custom pre-thread logic here
        // e.g., setup MDC, initialize thread-local variables, etc.
    }

    private void postThreadAction(String threadName, boolean isWorker) {
        System.out.println("POST: Finishing Vert.x thread: " + threadName +
                          " (worker: " + isWorker + ")");
        // Add your custom post-thread logic here
        // e.g., cleanup MDC, clear thread-local variables, etc.
    }
}

// Custom VertxThread that uses virtual threads for workers
class VirtualVertxThread extends VertxThread {
    private Thread virtualThread;

    public VirtualVertxThread(Runnable target, String name, boolean worker,
                             long maxExecTime, TimeUnit maxExecTimeUnit) {
        super(null, name, worker, maxExecTime, maxExecTimeUnit);

        // Create virtual thread for the actual work
        this.virtualThread = Thread.ofVirtual()
                .name(name)
                .unstarted(() -> {
                    // Set the current thread context for VertxThread
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