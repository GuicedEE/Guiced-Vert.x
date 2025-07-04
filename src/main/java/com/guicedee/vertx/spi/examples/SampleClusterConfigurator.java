package com.guicedee.vertx.spi.examples;

import com.guicedee.vertx.spi.ClusterVertxConfigurator;
import io.vertx.core.VertxBuilder;
import io.vertx.core.spi.cluster.ClusterManager;

/**
 * Sample implementation of ClusterVertxConfigurator to demonstrate how to configure
 * a cluster manager for Vertx.
 * 
 * This is an example class and should not be used in production.
 * To use your own cluster manager, create a similar class that implements
 * ClusterVertxConfigurator and returns your cluster manager instance.
 * 
 * Register your implementation through the ServiceLoader mechanism by creating
 * a file at:
 * META-INF/services/com.guicedee.vertx.spi.VertxConfigurator
 * 
 * The file should contain the fully qualified name of your implementation class.
 */
public class SampleClusterConfigurator implements ClusterVertxConfigurator {
    
    private final ClusterManager clusterManager;
    
    /**
     * Constructor that initializes the cluster manager.
     * In a real implementation, you would initialize your actual cluster manager here.
     */
    public SampleClusterConfigurator() {
        // In a real implementation, you would initialize your actual cluster manager
        // For example:
        // this.clusterManager = new HazelcastClusterManager();
        
        // This is just a placeholder for the example
        this.clusterManager = null;
    }
    
    @Override
    public ClusterManager getClusterManager() {
        return clusterManager;
    }
    
    /**
     * Optional: Override the default builder method if you need to apply
     * additional configurations beyond just setting the cluster manager.
     */
    @Override
    public VertxBuilder builder(VertxBuilder builder) {
        // First apply the cluster manager using the default implementation
        builder = ClusterVertxConfigurator.super.builder(builder);
        
        // Then apply any additional configurations
        // For example:
        // builder.with(new VertxOptions().setHAEnabled(true));
        
        return builder;
    }
}