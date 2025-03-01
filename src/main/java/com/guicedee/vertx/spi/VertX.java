package com.guicedee.vertx.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Annotation to configure runtime options for Vert.x applications.
 * The `VertX` annotation provides flexibility to configure multiple parameters such as pool sizes,
 * thread execution timeouts, and transport preferences.
 *
 * <p>This annotation can be applied at the package level or type level and is retained at runtime
 * to allow framework components to dynamically adapt Vert.x behaviors.</p>
 *
 * Usage
 * <code>
 * @VertX(eventLoopPoolSize = 8, workerPoolSize = 30, haEnabled = true, quorumSize = 3)
 * @MetricsOptions(enabled = true)
 * @FileSystemOptions(classPathResolvingEnabled = true, fileCachingEnabled = true, fileCacheDir = "/cache")
 * @EventBusOptions(clusterPublicHost = "192.168.1.1", clusterPublicPort = 8080)
 * @AddressResolverOptions(servers = {"8.8.8.8", "1.1.1.1"}, rotateServers = true)
 * public class MyVertxApplication {
 *     // Application logic here
 * }
 * </code>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface VertX {

    /**
     * Defines the size of the event loop thread pool.
     * This pool is used to handle event-driven tasks such as processing HTTP requests or event bus events.
     *
     * @return the number of threads in the event loop pool (default: 10)
     */
    int eventLoopPoolSize() default 10;

    /**
     * Specifies the size of the worker thread pool.
     * The worker pool handles blocking operations such as database queries or file system access.
     *
     * @return the number of threads in the worker pool (default: 20)
     */
    int workerPoolSize() default 20;

    /**
     * Specifies the interval at which blocked thread checks are performed, in the specified time unit.
     * A thread is considered "blocked" if it exceeds a predefined execution time threshold.
     *
     * @return the blocked thread check interval in the given time unit (default: 1000 ms)
     */
    long blockedThreadCheckInterval() default 1000;

    /**
     * The time unit for the blocked thread check interval.
     *
     * @return the time unit (default: {@link TimeUnit#MILLISECONDS})
     */
    TimeUnit blockedThreadCheckIntervalTimeUnit() default TimeUnit.MILLISECONDS;

    /**
     * The maximum allowed time for an event loop thread to execute before it is flagged as blocked.
     *
     * @return the maximum execution time for event loop threads (default: 2000)
     */
    long maxEventLoopExecuteTime() default 2000;

    /**
     * The time unit for the maximum event loop execute time.
     *
     * @return the time unit (default: {@link TimeUnit#MILLISECONDS})
     */
    TimeUnit maxEventLoopExecuteTimeUnit() default TimeUnit.MILLISECONDS;

    /**
     * Specifies the maximum time for a worker thread to execute before being flagged.
     * This allows detecting potential performance bottlenecks.
     *
     * @return the maximum execute time (default: 10,000)
     */
    long maxWorkerExecuteTime() default 10000;

    /**
     * The time unit for the maximum worker execute time.
     *
     * @return the time unit (default: {@link TimeUnit#MINUTES})
     */
    TimeUnit maxWorkerExecuteTimeUnit() default TimeUnit.MINUTES;

    /**
     * Defines the size of the internal blocking thread pool.
     * This pool is used for specific internal operations that can block threads.
     *
     * @return the size of the internal blocking pool (default: 20)
     */
    int internalBlockingPoolSize() default 20;

    /**
     * Indicates whether High-Availability (HA) mode is enabled.
     * This enables cluster-wide failover and state sharing for components in the application.
     *
     * @return {@code true} if HA is enabled, {@code false} otherwise (default: false)
     */
    boolean haEnabled() default false;

    /**
     * Specifies the quorum size, which is the minimum number of nodes required to form a cluster.
     *
     * @return the quorum size (default: 5)
     */
    int quorumSize() default 5;

    /**
     * The threshold at which a stack trace warning is logged for blocked threads.
     * If a thread exceeds this execution duration, stack trace warnings will appear in the logs.
     *
     * @return the warning exception time (default: 5000 milliseconds)
     */
    long warningExceptionTime() default 5000;

    /**
     * The time unit for the warning exception time.
     *
     * @return the time unit (default: {@link TimeUnit#MILLISECONDS})
     */
    TimeUnit warningExceptionTimeUnit() default TimeUnit.MILLISECONDS;

    /**
     * Indicates whether to prefer native transport capabilities where available.
     * Native transports may provide increased performance compared to JVM-only transport layers.
     *
     * @return {@code true} if native transport is preferred, {@code false} otherwise (default: true)
     */
    boolean preferNativeTransport() default true;

    /**
     * Indicates whether daemon threads should be used for worker and event loop threads.
     * Daemon threads do not prevent the JVM from shutting down.
     *
     * @return {@code true} if daemon threads are used, {@code false} otherwise (default: true)
     */
    boolean useDaemonThreads() default true;

    /**
     * Indicates whether the Thread Context Classloader (TCCL) is disabled.
     *
     * @return {@code true} if TCCL is disabled, {@code false} otherwise (default: true)
     */
    boolean disableTCCL() default true;

}