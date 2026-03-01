package com.guicedee.vertx.test;

import com.guicedee.client.services.IGuiceConfig;

/**
 * Minimal {@link IGuiceConfig} implementation for unit tests.
 * <p>
 * All flags default to {@code false} and setters return {@code this} so the
 * fluent configuration API used by production code works out of the box.
 */
public class TestGuiceConfig implements IGuiceConfig<TestGuiceConfig> {

    private boolean serviceLoadWithClassPath;
    private boolean fieldScanning;
    private boolean annotationScanning;
    private boolean methodInfo;
    private boolean ignoreFieldVisibility;
    private boolean ignoreMethodVisibility;
    private boolean ignoreClassVisibility;
    private boolean includePackages;
    private boolean fieldInfo;
    private boolean verbose;
    private boolean classpathScanning;
    private boolean excludeModulesAndJars;
    private boolean excludePaths;
    private boolean allowPaths;
    private boolean includeModuleAndJars;
    private boolean pathScanning;
    private boolean excludeParentModules;
    private boolean excludePackages;

    @Override public boolean isServiceLoadWithClassPath() { return serviceLoadWithClassPath; }
    @Override public TestGuiceConfig setServiceLoadWithClassPath(boolean v) { serviceLoadWithClassPath = v; return this; }

    @Override public boolean isFieldScanning() { return fieldScanning; }
    @Override public TestGuiceConfig setFieldScanning(boolean v) { fieldScanning = v; return this; }

    @Override public boolean isAnnotationScanning() { return annotationScanning; }
    @Override public TestGuiceConfig setAnnotationScanning(boolean v) { annotationScanning = v; return this; }

    @Override public boolean isMethodInfo() { return methodInfo; }
    @Override public TestGuiceConfig setMethodInfo(boolean v) { methodInfo = v; return this; }

    @Override public boolean isIgnoreFieldVisibility() { return ignoreFieldVisibility; }
    @Override public TestGuiceConfig setIgnoreFieldVisibility(boolean v) { ignoreFieldVisibility = v; return this; }

    @Override public boolean isIgnoreMethodVisibility() { return ignoreMethodVisibility; }
    @Override public TestGuiceConfig setIgnoreMethodVisibility(boolean v) { ignoreMethodVisibility = v; return this; }

    @Override public boolean isIncludePackages() { return includePackages; }
    @Override public TestGuiceConfig setIncludePackages(boolean v) { includePackages = v; return this; }

    @Override public boolean isFieldInfo() { return fieldInfo; }
    @Override public TestGuiceConfig setFieldInfo(boolean v) { fieldInfo = v; return this; }

    @Override public boolean isVerbose() { return verbose; }
    @Override public TestGuiceConfig setVerbose(boolean v) { verbose = v; return this; }

    @Override public boolean isClasspathScanning() { return classpathScanning; }
    @Override public TestGuiceConfig setClasspathScanning(boolean v) { classpathScanning = v; return this; }

    @Override public boolean isExcludeModulesAndJars() { return excludeModulesAndJars; }
    @Override public TestGuiceConfig setExcludeModulesAndJars(boolean v) { excludeModulesAndJars = v; return this; }

    @Override public boolean isExcludePaths() { return excludePaths; }
    @Override public TestGuiceConfig setExcludePaths(boolean v) { excludePaths = v; return this; }

    @Override public boolean isAllowPaths() { return allowPaths; }
    @Override public TestGuiceConfig setAllowPaths(boolean v) { allowPaths = v; return this; }

    @Override public boolean isIgnoreClassVisibility() { return ignoreClassVisibility; }
    @Override public TestGuiceConfig setIgnoreClassVisibility(boolean v) { ignoreClassVisibility = v; return this; }

    @Override public boolean isIncludeModuleAndJars() { return includeModuleAndJars; }
    @Override public TestGuiceConfig setIncludeModuleAndJars(boolean v) { includeModuleAndJars = v; return this; }

    @Override public boolean isPathScanning() { return pathScanning; }
    @Override public TestGuiceConfig setPathScanning(boolean v) { pathScanning = v; return this; }

    @Override public boolean isExcludeParentModules() { return excludeParentModules; }
    @Override public TestGuiceConfig setExcludeParentModules(boolean v) { excludeParentModules = v; return this; }

    @Override public boolean isRejectPackages() { return excludePackages; }
    @Override public TestGuiceConfig setExcludePackages(boolean v) { excludePackages = v; return this; }
}

