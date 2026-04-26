package com.guicedee.vertx.auth.abac;

import com.guicedee.client.Environment;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.auth.IGuicedAuthorizationProvider;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.github.classgraph.ScanResult;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.abac.Policy;
import io.vertx.ext.auth.abac.PolicyBasedAuthorizationProvider;
import io.vertx.ext.auth.authorization.AuthorizationProvider;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ServiceLoader;
import java.util.Set;

/**
 * Auto-discovered ABAC authorization provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when an {@link AbacOptions} annotation is found
 * on a class or {@code package-info.java}. Implements {@link IGuicedAuthorizationProvider}
 * so it is automatically registered into the authorization provider set.
 * <p>
 * Policies are loaded from three sources (all additive):
 * <ol>
 *   <li>Inline JSON strings from {@link AbacOptions#policies()}</li>
 *   <li>JSON files from {@link AbacOptions#policyFiles()}</li>
 *   <li>SPI implementations of {@link IAbacPolicyProvider}</li>
 * </ol>
 *
 * <h3>Environment variable overrides</h3>
 * <ul>
 *   <li>{@code VERTX_AUTH_ABAC_POLICY_FILES} — comma-separated list of policy file paths (overrides annotation)</li>
 * </ul>
 */
@Log4j2
public class AbacAuthorizationProvider implements IGuicedAuthorizationProvider
{
    @Getter
    private static PolicyBasedAuthorizationProvider policyProvider;

    @Getter
    private static AbacOptions abacOptions;

    @Override
    public AuthorizationProvider getAuthorizationProvider()
    {
        ScanResult scanResult = IGuiceContext.instance().getScanResult();
        abacOptions = discoverAbacOptions(scanResult);

        if (abacOptions == null)
        {
            log.debug("No @AbacOptions annotation found — ABAC provider not activated");
            return null;
        }

        Vertx vertx = VertXPreStartup.getVertx();

        try
        {
            policyProvider = PolicyBasedAuthorizationProvider.create();
            int count = 0;

            // 1. Inline policies
            for (String policyJson : abacOptions.policies())
            {
                String resolved = resolveEnvPlaceholders(policyJson);
                if (!resolved.isBlank())
                {
                    Policy policy = new Policy(new JsonObject(resolved));
                    policyProvider.addPolicy(policy);
                    count++;
                    log.debug("ABAC: Loaded inline policy: {}", policy.getName());
                }
            }

            // 2. Policy files
            String envFiles = env("POLICY_FILES", null);
            String[] files;
            if (envFiles != null && !envFiles.isBlank())
            {
                files = envFiles.split(",");
            }
            else
            {
                files = abacOptions.policyFiles();
            }

            for (String filePath : files)
            {
                String resolved = resolveEnvPlaceholders(filePath.trim());
                if (resolved.isBlank()) continue;

                try
                {
                    String content = vertx.fileSystem().readFileBlocking(resolved).toString();
                    content = content.trim();

                    if (content.startsWith("["))
                    {
                        // Array of policies
                        JsonArray array = new JsonArray(content);
                        for (int i = 0; i < array.size(); i++)
                        {
                            Policy policy = new Policy(array.getJsonObject(i));
                            policyProvider.addPolicy(policy);
                            count++;
                            log.debug("ABAC: Loaded policy from file {}: {}", resolved, policy.getName());
                        }
                    }
                    else
                    {
                        // Single policy
                        Policy policy = new Policy(new JsonObject(content));
                        policyProvider.addPolicy(policy);
                        count++;
                        log.debug("ABAC: Loaded policy from file {}: {}", resolved, policy.getName());
                    }
                }
                catch (Exception e)
                {
                    log.error("ABAC: Failed to load policy file: {}", resolved, e);
                }
            }

            // 3. SPI policy providers
            @SuppressWarnings("rawtypes")
            Set<IAbacPolicyProvider> spiProviders = IGuiceContext.instance()
                    .getLoader(IAbacPolicyProvider.class, true, ServiceLoader.load(IAbacPolicyProvider.class));
            for (IAbacPolicyProvider spiProvider : spiProviders)
            {
                var policies = spiProvider.getPolicies();
                if (policies != null)
                {
                    for (Policy policy : policies)
                    {
                        policyProvider.addPolicy(policy);
                        count++;
                        log.debug("ABAC: Loaded SPI policy from {}: {}",
                                spiProvider.getClass().getName(), policy.getName());
                    }
                }
            }

            if (count == 0)
            {
                log.warn("ABAC: No policies loaded — provider will grant no authorizations");
            }
            else
            {
                log.info("ABAC authorization provider created with {} policy/policies", count);
            }

            return policyProvider;
        }
        catch (Exception e)
        {
            log.error("Failed to create ABAC authorization provider", e);
            return null;
        }
    }

    // ── Annotation discovery ────────────────────────────

    private AbacOptions discoverAbacOptions(ScanResult scanResult)
    {
        var annotatedClasses = scanResult.getClassesWithAnnotation(AbacOptions.class);
        for (var ci : annotatedClasses)
        {
            var ann = ci.loadClass().getAnnotation(AbacOptions.class);
            if (ann != null)
            {
                log.debug("Found @AbacOptions on class: {}", ci.getName());
                return ann;
            }
        }

        var packageInfoList = scanResult.getPackageInfo();
        for (var pkgInfo : packageInfoList)
        {
            try
            {
                Class<?> pkgClass = Class.forName(pkgInfo.getName() + ".package-info");
                var ann = pkgClass.getAnnotation(AbacOptions.class);
                if (ann != null)
                {
                    log.debug("Found @AbacOptions on package: {}", pkgInfo.getName());
                    return ann;
                }
            }
            catch (ClassNotFoundException _) { }
        }

        return null;
    }

    // ── Env / placeholder helpers ───────────────────────

    static String env(String property, String defaultValue)
    {
        String value = Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_ABAC_" + property, null);
        if (value != null && !value.isBlank())
        {
            return value;
        }
        return defaultValue;
    }

    private static String resolveEnvPlaceholders(String value)
    {
        if (value == null || !value.contains("${"))
        {
            return value;
        }
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < value.length())
        {
            if (value.charAt(i) == '$' && i + 1 < value.length() && value.charAt(i + 1) == '{')
            {
                int end = value.indexOf('}', i + 2);
                if (end > 0)
                {
                    String varName = value.substring(i + 2, end);
                    String resolved = Environment.getSystemPropertyOrEnvironment(varName, "");
                    result.append(resolved);
                    i = end + 1;
                    continue;
                }
            }
            result.append(value.charAt(i));
            i++;
        }
        return result.toString();
    }

    /**
     * Resets all state — for testing purposes.
     */
    public static void reset()
    {
        if (policyProvider != null)
        {
            policyProvider.clear();
        }
        policyProvider = null;
        abacOptions = null;
    }

    @Override
    public Integer sortOrder()
    {
        return 80;
    }
}

