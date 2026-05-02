package com.guicedee.vertx.grpc;

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
 * Discovers {@link GrpcOptions} annotations at startup and builds
 * {@link GrpcConnectionInfo} instances that are used by the GrpcModule.
 * <p>
 * Scans all classes and packages for {@code @GrpcOptions} annotations,
 * resolves environment variable placeholders, and stores the resulting configurations.
 * <p>
 * Runs after {@code VertXPreStartup} (sort order MIN+62) to ensure
 * the Vert.x instance is available.
 */
@Log4j2
public class GrpcPreStartup implements IGuicePreStartup<GrpcPreStartup> {

    @Getter
    private static final List<GrpcConnectionInfo> discoveredConnections = new ArrayList<>();

    @Override
    public List<Future<Boolean>> onStartup() {
        discoveredConnections.clear();

        ScanResult scanResult = IGuiceContext.instance().getScanResult();
        if (scanResult == null) {
            log.warn("⚠️ No scan result available — skipping gRPC annotation discovery");
            return List.of();
        }

        // Scan classes for @GrpcOptions
        ClassInfoList annotatedClasses = scanResult.getClassesWithAnnotation(GrpcOptions.class.getName());
        for (ClassInfo classInfo : annotatedClasses) {
            processAnnotations(classInfo.loadClass().getAnnotationsByType(GrpcOptions.class));
        }

        // Scan package-info classes for @GrpcOptions
        ClassInfoList packageInfoClasses = scanResult.getClassesWithAnnotation(GrpcOptionsContainer.class.getName());
        for (ClassInfo classInfo : packageInfoClasses) {
            processAnnotations(classInfo.loadClass().getAnnotationsByType(GrpcOptions.class));
        }

        if (!discoveredConnections.isEmpty()) {
            log.info("📡 Discovered {} gRPC configuration(s) via @GrpcOptions annotations", discoveredConnections.size());
        }

        return List.of();
    }

    private void processAnnotations(GrpcOptions[] annotations) {
        for (GrpcOptions opts : annotations) {
            GrpcConnectionInfo info = buildFromAnnotation(opts);
            discoveredConnections.add(info);
            log.debug("  ↳ gRPC config '{}' — {}:{}", info.getName(), info.getHost(), info.getPort());
        }
    }

    /**
     * Builds a {@link GrpcConnectionInfo} from a {@link GrpcOptions} annotation,
     * resolving all environment variable placeholders.
     */
    public static GrpcConnectionInfo buildFromAnnotation(GrpcOptions opts) {
        GrpcConnectionInfo info = new GrpcConnectionInfo();

        // Name
        info.setName(resolve(opts.name(), "GRPC_NAME", "default"));

        // Host
        info.setHost(resolve(opts.host(), "GRPC_HOST", "0.0.0.0"));

        // Port
        String portOverride = Environment.getSystemPropertyOrEnvironment("GRPC_PORT", null);
        info.setPort(portOverride != null && !portOverride.isBlank() ? Integer.parseInt(portOverride) : opts.port());

        // TLS
        String tlsOverride = Environment.getSystemPropertyOrEnvironment("GRPC_TLS_ENABLED", null);
        info.setTlsEnabled(tlsOverride != null && !tlsOverride.isBlank() ? Boolean.parseBoolean(tlsOverride) : opts.tlsEnabled());

        String certPath = resolve(opts.tlsCertPath(), "GRPC_TLS_CERT_PATH", "");
        if (!certPath.isBlank()) {
            info.setTlsCertPath(certPath);
        }

        String keyPath = resolve(opts.tlsKeyPath(), "GRPC_TLS_KEY_PATH", "");
        if (!keyPath.isBlank()) {
            info.setTlsKeyPath(keyPath);
        }

        // Max message size
        String maxMsgOverride = Environment.getSystemPropertyOrEnvironment("GRPC_MAX_MESSAGE_SIZE", null);
        info.setMaxMessageSize(maxMsgOverride != null && !maxMsgOverride.isBlank() ? Integer.parseInt(maxMsgOverride) : opts.maxMessageSize());

        // Deadline propagation
        String deadlineOverride = Environment.getSystemPropertyOrEnvironment("GRPC_DEADLINE_PROPAGATION", null);
        info.setDeadlinePropagation(deadlineOverride != null && !deadlineOverride.isBlank() ? Boolean.parseBoolean(deadlineOverride) : opts.deadlinePropagation());

        // Schedule deadline automatically
        String scheduleOverride = Environment.getSystemPropertyOrEnvironment("GRPC_SCHEDULE_DEADLINE", null);
        info.setScheduleDeadlineAutomatically(scheduleOverride != null && !scheduleOverride.isBlank() ? Boolean.parseBoolean(scheduleOverride) : opts.scheduleDeadlineAutomatically());

        // gRPC-Web
        String webOverride = Environment.getSystemPropertyOrEnvironment("GRPC_WEB_ENABLED", null);
        info.setGrpcWebEnabled(webOverride != null && !webOverride.isBlank() ? Boolean.parseBoolean(webOverride) : opts.grpcWebEnabled());

        // Transcoding
        String transcodingOverride = Environment.getSystemPropertyOrEnvironment("GRPC_TRANSCODING_ENABLED", null);
        info.setTranscodingEnabled(transcodingOverride != null && !transcodingOverride.isBlank() ? Boolean.parseBoolean(transcodingOverride) : opts.transcodingEnabled());

        // Default connection
        info.setDefaultConnection(opts.defaultConnection());

        return info;
    }

    /**
     * Resolves a value from annotation, then env override, then placeholder resolution.
     */
    private static String resolve(String annotationValue, String envKey, String fallback) {
        String envValue = Environment.getSystemPropertyOrEnvironment(envKey, null);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
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
        return Integer.MIN_VALUE + 62;
    }
}




