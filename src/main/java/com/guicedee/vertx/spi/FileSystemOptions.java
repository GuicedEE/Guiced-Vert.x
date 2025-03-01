package com.guicedee.vertx.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface FileSystemOptions
{
    /**
     * Enables or disables class path resolution.
     * If enabled, Vert.x attempts to resolve files from the classpath when not found on the filesystem.
     *
     * @return {@code true} if class path resolution is enabled, {@code false} otherwise
     */
    boolean classPathResolvingEnabled() default false;

    /**
     * Enables or disables file caching for class path resolution.
     * If enabled, Vert.x stores resolved classpath resources on the filesystem for faster access.
     *
     * @return {@code true} if file caching is enabled, {@code false} otherwise
     */
    boolean fileCachingEnabled() default false;

    /**
     * The directory used for file caching during class path resolution.
     * Defaults to the system's temporary directory or a dedicated cache directory.
     *
     * @return the path to the file caching directory (default: system specific)
     */
    String fileCacheDir() default "";

}
