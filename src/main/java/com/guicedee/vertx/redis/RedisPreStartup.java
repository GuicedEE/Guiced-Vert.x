package com.guicedee.vertx.redis;

import com.guicedee.client.Environment;
import com.guicedee.client.IGuiceContext;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import io.vertx.core.Future;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers {@link RedisOptions} annotations at startup and builds
 * {@link RedisConnectionInfo} instances that are used by the {@link RedisModule}.
 * <p>
 * Scans all classes and packages for {@code @RedisOptions} annotations,
 * resolves environment variable placeholders, and stores the resulting configurations.
 * <p>
 * Runs after {@code VertXPreStartup} (sort order MIN+60) to ensure
 * the Vert.x instance is available.
 */
@Log4j2
public class RedisPreStartup implements IGuicePreStartup<RedisPreStartup> {

    @Getter
    private static final List<RedisConnectionInfo> discoveredConnections = new ArrayList<>();

    @Override
    public List<Future<Boolean>> onStartup() {
        discoveredConnections.clear();

        ScanResult scanResult = IGuiceContext.instance().getScanResult();
        if (scanResult == null) {
            log.warn("⚠️ No scan result available — skipping Redis annotation discovery");
            return List.of();
        }

        // Scan classes for @RedisOptions
        ClassInfoList annotatedClasses = scanResult.getClassesWithAnnotation(RedisOptions.class.getName());
        for (ClassInfo classInfo : annotatedClasses) {
            processAnnotations(classInfo.loadClass().getAnnotationsByType(RedisOptions.class));
        }

        // Scan package-info classes for @RedisOptions
        ClassInfoList packageInfoClasses = scanResult.getClassesWithAnnotation(RedisOptionsContainer.class.getName());
        for (ClassInfo classInfo : packageInfoClasses) {
            processAnnotations(classInfo.loadClass().getAnnotationsByType(RedisOptions.class));
        }

        if (!discoveredConnections.isEmpty()) {
            log.info("🔴 Discovered {} Redis connection(s) via @RedisOptions annotations", discoveredConnections.size());
        }

        return List.of();
    }

    private void processAnnotations(RedisOptions[] annotations) {
        for (RedisOptions opts : annotations) {
            RedisConnectionInfo info = buildFromAnnotation(opts);
            discoveredConnections.add(info);
            log.debug("  ↳ Redis connection '{}' — {} [{}]",
                    info.getName(), info.getConnectionString(), info.getRedisMode());
        }
    }

    /**
     * Builds a {@link RedisConnectionInfo} from a {@link RedisOptions} annotation,
     * resolving all environment variable placeholders.
     */
    public static RedisConnectionInfo buildFromAnnotation(RedisOptions opts) {
        RedisConnectionInfo info = new RedisConnectionInfo();

        // Name
        info.setName(resolve(opts.name(), "REDIS_NAME", "default"));

        // Connection string
        info.setConnectionString(resolve(opts.connectionString(), "REDIS_CONNECTION_STRING", "redis://localhost:6379"));

        // Additional endpoints
        for (String endpoint : opts.endpoints()) {
            String resolved = resolveEnvPlaceholders(endpoint);
            if (resolved != null && !resolved.isBlank()) {
                info.addEndpoint(resolved);
            }
        }

        // Mode
        String modeOverride = Environment.getSystemPropertyOrEnvironment("REDIS_MODE", null);
        if (modeOverride != null && !modeOverride.isBlank()) {
            info.setRedisMode(RedisConnectionInfo.RedisMode.valueOf(modeOverride.toUpperCase()));
        } else {
            info.setRedisMode(switch (opts.mode()) {
                case STANDALONE -> RedisConnectionInfo.RedisMode.STANDALONE;
                case SENTINEL -> RedisConnectionInfo.RedisMode.SENTINEL;
                case CLUSTER -> RedisConnectionInfo.RedisMode.CLUSTER;
                case REPLICATION -> RedisConnectionInfo.RedisMode.REPLICATION;
            });
        }

        // Password
        String password = resolve(opts.password(), "REDIS_PASSWORD", "");
        if (!password.isBlank()) {
            info.setPassword(password);
        }

        // Pool size
        String poolOverride = Environment.getSystemPropertyOrEnvironment("REDIS_MAX_POOL_SIZE", null);
        info.setMaxPoolSize(poolOverride != null && !poolOverride.isBlank() ? Integer.parseInt(poolOverride) : opts.maxPoolSize());

        // Max waiting
        String waitingOverride = Environment.getSystemPropertyOrEnvironment("REDIS_MAX_WAITING", null);
        info.setMaxWaitingHandlers(waitingOverride != null && !waitingOverride.isBlank() ? Integer.parseInt(waitingOverride) : opts.maxWaitingHandlers());

        // TLS
        String tlsOverride = Environment.getSystemPropertyOrEnvironment("REDIS_TLS_ENABLED", null);
        info.setTlsEnabled(tlsOverride != null ? Boolean.parseBoolean(tlsOverride) : opts.tlsEnabled());

        String certPath = resolve(opts.tlsCertPath(), "REDIS_TLS_CERT_PATH", "");
        if (!certPath.isBlank()) {
            info.setTrustCertPath(certPath);
        }

        String verifyHost = resolve(opts.tlsVerifyHost(), "REDIS_TLS_VERIFY_HOST", "");
        info.setHostnameVerificationAlgorithm(verifyHost);

        // Protocol
        String protocolOverride = Environment.getSystemPropertyOrEnvironment("REDIS_PROTOCOL", null);
        if (protocolOverride != null && !protocolOverride.isBlank()) {
            info.setPreferredProtocol(RedisConnectionInfo.RedisProtocol.valueOf(protocolOverride.toUpperCase()));
        } else if (opts.protocol() != RedisOptions.Protocol.AUTO) {
            info.setPreferredProtocol(switch (opts.protocol()) {
                case RESP2 -> RedisConnectionInfo.RedisProtocol.RESP2;
                case RESP3 -> RedisConnectionInfo.RedisProtocol.RESP3;
                default -> null;
            });
        }

        // Master name (sentinel)
        info.setMasterName(resolve(opts.masterName(), "REDIS_MASTER_NAME", "mymaster"));

        // Database
        String dbOverride = Environment.getSystemPropertyOrEnvironment("REDIS_DATABASE", null);
        info.setDatabase(dbOverride != null && !dbOverride.isBlank() ? Integer.parseInt(dbOverride) : opts.database());

        // Default connection
        info.setDefaultConnection(opts.defaultConnection());

        return info;
    }

    /**
     * Resolves a value from annotation, then env override, then placeholder resolution.
     */
    private static String resolve(String annotationValue, String envKey, String fallback) {
        // Check env override first
        String envValue = Environment.getSystemPropertyOrEnvironment(envKey, null);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        // Resolve placeholders in annotation value
        if (annotationValue != null && !annotationValue.isBlank()) {
            return resolveEnvPlaceholders(annotationValue);
        }
        return fallback;
    }

    /**
     * Resolves {@code ${ENV_VAR:default}} placeholders in a string value.
     */
    public static String resolveEnvPlaceholders(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            if (i < value.length() - 1 && value.charAt(i) == '$' && value.charAt(i + 1) == '{') {
                int end = value.indexOf('}', i + 2);
                if (end == -1) {
                    result.append(value.substring(i));
                    break;
                }
                String placeholder = value.substring(i + 2, end);
                String envName;
                String defaultValue = "";
                int colonIdx = placeholder.indexOf(':');
                if (colonIdx >= 0) {
                    envName = placeholder.substring(0, colonIdx);
                    defaultValue = placeholder.substring(colonIdx + 1);
                } else {
                    envName = placeholder;
                }
                String resolved = Environment.getSystemPropertyOrEnvironment(envName, defaultValue);
                result.append(resolved != null ? resolved : defaultValue);
                i = end + 1;
            } else {
                result.append(value.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    @Override
    public Integer sortOrder() {
        return Integer.MIN_VALUE + 60;
    }
}







