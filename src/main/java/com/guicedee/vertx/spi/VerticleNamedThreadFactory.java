package com.guicedee.vertx.spi;

import io.vertx.core.impl.VertxThread;
import io.vertx.core.spi.VertxThreadFactory;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.TimeUnit;

/**
 * Custom {@link VertxThreadFactory} that creates threads using the Vert.x-provided
 * thread name (which already includes the worker pool name for worker threads).
 * <p>
 * For event loop threads, the name format is {@code vert.x-eventloop-thread-N}.
 * For worker pool threads deployed with a named pool, the format is {@code <workerPoolName>-N}.
 * <p>
 * This factory preserves those names and ensures threads are properly configured
 * (daemon status, blocked-thread detection parameters).
 */
@Log4j2
public class VerticleNamedThreadFactory implements VertxThreadFactory {

    @Override
    public VertxThread newVertxThread(Runnable target, String name, boolean worker, long maxExecTime, TimeUnit maxExecTimeUnit) {
        // Vert.x passes the correct thread name:
        //   - "vert.x-eventloop-thread-N" for event loop threads
        //   - "<workerPoolName>-N" for named worker pool threads
        //   - "vert.x-worker-thread-N" for default worker pool threads
        // We create the thread with this name directly.
        VertxThread thread = VertxThreadFactory.super.newVertxThread(target, name, worker, maxExecTime, maxExecTimeUnit);
        log.trace("Created Vert.x thread: name={}, worker={}, maxExecTime={} {}", name, worker, maxExecTime, maxExecTimeUnit);
        return thread;
    }
}


