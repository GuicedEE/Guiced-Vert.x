package com.guicedee.vertx.auth;

import com.guicedee.client.Environment;
import com.guicedee.client.IGuiceContext;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import io.vertx.core.Future;
import io.vertx.ext.auth.ChainAuth;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.authorization.AuthorizationProvider;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import io.github.classgraph.ClassInfo;

import java.util.*;

/**
 * Discovers {@link AuthOptions} annotations at startup and builds the
 * authentication/authorization provider chain.
 * <p>
 * Runs after {@code VertXPreStartup} (sort order MIN+50) to ensure
 * the Vert.x instance is available.
 */
@Log4j2
public class VertxAuthPreStartup implements IGuicePreStartup<VertxAuthPreStartup>
{
    @Getter
    private static AuthOptions authOptions;

    @Getter
    private static ChainAuth chainAuth;

    @Getter
    private static final List<AuthenticationProvider> authenticationProviders = new ArrayList<>();

    @Getter
    private static final List<AuthorizationProvider> authorizationProviders = new ArrayList<>();

    @Getter
    private static KeyStoreOptions keyStoreOptions;

    @Getter
    private static final List<PubSecKeyOptions> pubSecKeyOptionsList = new ArrayList<>();

    @Override
    public List<Future<Boolean>> onStartup()
    {
        ScanResult scanResult = IGuiceContext.instance().getScanResult();
        discoverAuthOptions(scanResult);

        if (authOptions != null)
        {
            applyAuthOptions(authOptions);
        }


        // Get the service set via standard GuicedEE loading (handles ServiceLoader + ClassGraph)
        // Then dynamically add ClassGraph-discovered providers for optional auth modules
        // that aren't declared in module-info provides (to avoid ClassNotFoundException with requires static)
        @SuppressWarnings("unchecked")
        Set<IGuicedAuthenticationProvider> authProviderSet = IGuiceContext.loaderToSetNoInjection(ServiceLoader.load(IGuicedAuthenticationProvider.class));
        for (ClassInfo classInfo : scanResult.getClassesImplementing(IGuicedAuthenticationProvider.class))
        {
            try
            {
                Class<? extends IGuicedAuthenticationProvider> implClass = (Class<? extends IGuicedAuthenticationProvider>) classInfo.loadClass();
                if (implClass.isInterface() || java.lang.reflect.Modifier.isAbstract(implClass.getModifiers()))
                {
                    continue;
                }
                // Only add if not already present from ServiceLoader
                if (authProviderSet.stream().noneMatch(p -> p.getClass().equals(implClass)))
                {
                    IGuicedAuthenticationProvider instance = implClass.getDeclaredConstructor().newInstance();
                    authProviderSet.add(instance);
                    log.debug("Dynamically added authentication provider: {}", implClass.getName());
                }
            }
            catch (NoClassDefFoundError e)
            {
                log.debug("Skipping authentication provider {} - optional dependency not available: {}", classInfo.getName(), e.getMessage());
            }
            catch (Exception e)
            {
                log.warn("Failed to instantiate authentication provider {}: {}", classInfo.getName(), e.getMessage());
            }
        }
        IGuiceContext.getAllLoadedServices().put(IGuicedAuthenticationProvider.class, authProviderSet);
        for (IGuicedAuthenticationProvider spiProvider : authProviderSet)
        {
            AuthenticationProvider provider = spiProvider.getAuthenticationProvider();
            if (provider != null)
            {
                authenticationProviders.add(provider);
                log.info("Registered authentication provider: {}", spiProvider.getClass().getName());
            }
        }

        // Same pattern for authorization providers
        @SuppressWarnings("unchecked")
        Set<IGuicedAuthorizationProvider> authzProviderSet = IGuiceContext.loaderToSetNoInjection(ServiceLoader.load(IGuicedAuthorizationProvider.class));
        for (ClassInfo classInfo : scanResult.getClassesImplementing(IGuicedAuthorizationProvider.class))
        {
            try
            {
                Class<? extends IGuicedAuthorizationProvider> implClass = (Class<? extends IGuicedAuthorizationProvider>) classInfo.loadClass();
                if (implClass.isInterface() || java.lang.reflect.Modifier.isAbstract(implClass.getModifiers()))
                {
                    continue;
                }
                if (authzProviderSet.stream().noneMatch(p -> p.getClass().equals(implClass)))
                {
                    IGuicedAuthorizationProvider instance = implClass.getDeclaredConstructor().newInstance();
                    authzProviderSet.add(instance);
                    log.debug("Dynamically added authorization provider: {}", implClass.getName());
                }
            }
            catch (NoClassDefFoundError e)
            {
                log.debug("Skipping authorization provider {} - optional dependency not available: {}", classInfo.getName(), e.getMessage());
            }
            catch (Exception e)
            {
                log.warn("Failed to instantiate authorization provider {}: {}", classInfo.getName(), e.getMessage());
            }
        }
        IGuiceContext.getAllLoadedServices().put(IGuicedAuthorizationProvider.class, authzProviderSet);
        for (IGuicedAuthorizationProvider spiProvider : authzProviderSet)
        {
            AuthorizationProvider provider = spiProvider.getAuthorizationProvider();
            if (provider != null)
            {
                authorizationProviders.add(provider);
                log.info("Registered authorization provider: {}", spiProvider.getClass().getName());
            }
        }

        // Build ChainAuth if there are authentication providers
        if (!authenticationProviders.isEmpty())
        {
            AuthOptions.ChainMode mode = authOptions != null ? authOptions.chainMode() : AuthOptions.ChainMode.ANY;
            String modeEnv = env("CHAIN_MODE", mode.name());
            AuthOptions.ChainMode resolvedMode = AuthOptions.ChainMode.valueOf(modeEnv);

            chainAuth = resolvedMode == AuthOptions.ChainMode.ALL ? ChainAuth.all() : ChainAuth.any();
            for (AuthenticationProvider provider : authenticationProviders)
            {
                chainAuth.add(provider);
            }
            log.info("Built ChainAuth ({}) with {} authentication provider(s)", resolvedMode, authenticationProviders.size());
        }

        // Configure PRNG system properties
        configurePrng();

        log.info("Vert.x auth startup complete: {} authentication provider(s), {} authorization provider(s)",
                authenticationProviders.size(), authorizationProviders.size());

        return List.of(Future.succeededFuture(true));
    }

    private void discoverAuthOptions(ScanResult scanResult)
    {
        // Check classes
        ClassInfoList annotatedClasses = scanResult.getClassesWithAnnotation(AuthOptions.class);
        for (var ci : annotatedClasses)
        {
            var ann = ci.loadClass().getAnnotation(AuthOptions.class);
            if (ann != null)
            {
                authOptions = wrapAuthOptions(ann);
                log.debug("Found @AuthOptions on class: {}", ci.getName());
                return;
            }
        }

        // Check package-info
        var packageInfoList = scanResult.getPackageInfo();
        if (packageInfoList != null)
        {
            for (var pkgInfo : packageInfoList)
            {
                try
                {
                    Class<?> pkgClass = Class.forName(pkgInfo.getName() + ".package-info");
                    var ann = pkgClass.getAnnotation(AuthOptions.class);
                    if (ann != null)
                    {
                        authOptions = wrapAuthOptions(ann);
                        log.debug("Found @AuthOptions on package: {}", pkgInfo.getName());
                        return;
                    }
                }
                catch (ClassNotFoundException ignored) {}
            }
        }
    }

    private AuthOptions wrapAuthOptions(AuthOptions ann)
    {
        return new AuthOptions()
        {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { return AuthOptions.class; }

            @Override
            public ChainMode chainMode() { return ChainMode.valueOf(env("CHAIN_MODE", ann.chainMode().name())); }

            @Override
            public AuthKeyStore keyStore() { return ann.keyStore(); }

            @Override
            public AuthPubSecKey[] pubSecKeys() { return ann.pubSecKeys(); }

            @Override
            public String prngAlgorithm() { return env("PRNG_ALGORITHM", ann.prngAlgorithm()); }

            @Override
            public long prngSeedInterval() { return Long.parseLong(env("PRNG_SEED_INTERVAL", String.valueOf(ann.prngSeedInterval()))); }

            @Override
            public int prngSeedBits() { return Integer.parseInt(env("PRNG_SEED_BITS", String.valueOf(ann.prngSeedBits()))); }

            @Override
            public int leeway() { return Integer.parseInt(env("LEEWAY", String.valueOf(ann.leeway()))); }
        };
    }

    private void applyAuthOptions(AuthOptions opts)
    {
        // KeyStore configuration
        AuthKeyStore ks = opts.keyStore();
        String ksPath = env("KEYSTORE_PATH", ks.path());
        if (!ksPath.isBlank())
        {
            keyStoreOptions = new KeyStoreOptions()
                    .setPath(ksPath)
                    .setType(env("KEYSTORE_TYPE", ks.type()))
                    .setPassword(env("KEYSTORE_PASSWORD", ks.password()));

            String alias = env("KEYSTORE_ALIAS", ks.alias());
            String aliasPassword = env("KEYSTORE_ALIAS_PASSWORD", ks.aliasPassword());
            if (!alias.isBlank() && !aliasPassword.isBlank())
            {
                keyStoreOptions.putPasswordProtection(alias, aliasPassword);
            }

            log.info("Configured KeyStore: path={}, type={}", ksPath, keyStoreOptions.getType());
        }

        // PubSecKey configuration
        for (int i = 0; i < opts.pubSecKeys().length; i++)
        {
            AuthPubSecKey psk = opts.pubSecKeys()[i];
            String algorithm = env("PUBSECKEY_" + i + "_ALGORITHM", psk.algorithm());
            String buffer = env("PUBSECKEY_" + i + "_BUFFER", psk.buffer());
            String path = env("PUBSECKEY_" + i + "_PATH", psk.path());

            if (!algorithm.isBlank())
            {
                PubSecKeyOptions options = new PubSecKeyOptions().setAlgorithm(algorithm);
                if (!buffer.isBlank())
                {
                    options.setBuffer(io.vertx.core.buffer.Buffer.buffer(buffer));
                }
                else if (!path.isBlank())
                {
                    // Load PEM content from file path using Vert.x filesystem
                    try
                    {
                        io.vertx.core.buffer.Buffer fileContent = com.guicedee.vertx.spi.VertXPreStartup.getVertx()
                                .fileSystem().readFileBlocking(path);
                        options.setBuffer(fileContent);
                        log.info("Loaded PEM key from file: {}", path);
                    }
                    catch (Exception e)
                    {
                        log.error("Failed to load PEM key from file: {}", path, e);
                    }
                }
                pubSecKeyOptionsList.add(options);
                log.info("Configured PubSecKey[{}]: algorithm={}", i, algorithm);
            }
        }
    }

    private void configurePrng()
    {
        if (authOptions == null)
        {
            return;
        }

        String prngAlgorithm = authOptions.prngAlgorithm();
        if (!prngAlgorithm.isBlank())
        {
            System.setProperty("io.vertx.ext.auth.prng.algorithm", prngAlgorithm);
            log.info("PRNG algorithm set to: {}", prngAlgorithm);
        }

        long seedInterval = authOptions.prngSeedInterval();
        if (seedInterval > 0)
        {
            System.setProperty("io.vertx.ext.auth.prng.seed.interval", String.valueOf(seedInterval));
            log.info("PRNG seed interval set to: {}ms", seedInterval);
        }

        int seedBits = authOptions.prngSeedBits();
        if (seedBits > 0)
        {
            System.setProperty("io.vertx.ext.auth.prng.seed.bits", String.valueOf(seedBits));
            log.info("PRNG seed bits set to: {}", seedBits);
        }
    }

    static String env(String property, String defaultValue)
    {
        String value = Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_" + property, null);
        if (value != null && !value.isBlank())
        {
            return value;
        }
        return defaultValue;
    }

    /**
     * Resets all state — for testing purposes.
     */
    public static void reset()
    {
        authOptions = null;
        chainAuth = null;
        authenticationProviders.clear();
        authorizationProviders.clear();
        keyStoreOptions = null;
        pubSecKeyOptionsList.clear();
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 50;
    }
}

