package com.guicedee.vertx.spi;

import io.vertx.core.VertxBuilder;
import io.vertx.core.spi.cluster.ClusterManager;

/**
 * Interface for configuring the cluster manager in a Vertx instance.
 * Implementations of this interface can be registered through the ServiceLoader
 * to provide custom cluster configuration during VertxPreStartup.
 * 
 * To use this interface:
 * 1. Create a class that implements ClusterVertxConfigurator
 * 2. Implement the getClusterManager method to return your ClusterManager instance
 * 3. Optionally override the builder method if you need additional configuration
 * 4. Register your implementation through the ServiceLoader mechanism
 * 
 * To register your implementation, create a file at:
 * META-INF/services/com.guicedee.vertx.spi.VertxConfigurator
 * 
 * The file should contain the fully qualified name of your implementation class.
 * 
 * Example usage:
 * <pre>
 * public class MyClusterConfigurator implements ClusterVertxConfigurator {
 *     private final ClusterManager clusterManager;
 *     
 *     public MyClusterConfigurator() {
 *         // Initialize your cluster manager
 *         this.clusterManager = new HazelcastClusterManager();
 *     }
 *     
 *     @Override
 *     public ClusterManager getClusterManager() {
 *         return clusterManager;
 *     }
 *     
 *     // The default implementation of builder will configure the cluster manager
 *     // You can override it if you need additional configuration
 *     @Override
 *     public VertxBuilder builder(VertxBuilder builder) {
 *         // First apply the cluster manager using the default implementation
 *         builder = ClusterVertxConfigurator.super.builder(builder);
 *         
 *         // Then apply any additional configurations
 *         // For example:
 *         // builder.with(new VertxOptions().setHAEnabled(true));
 *         
 *         return builder;
 *     }
 * }
 * </pre>
 */
public interface ClusterVertxConfigurator extends VertxConfigurator {

    /**
     * Gets the ClusterManager to be used with Vertx.
     * 
     * @return the ClusterManager instance
     */
    ClusterManager getClusterManager();

    /**
     * Configures the VertxBuilder with the cluster manager.
     * Default implementation adds the cluster manager to the builder.
     * 
     * @param builder the VertxBuilder to configure
     * @return the configured VertxBuilder
     */
    @Override
    default VertxBuilder builder(VertxBuilder builder) {
        return builder.withClusterManager(getClusterManager());
    }
}
