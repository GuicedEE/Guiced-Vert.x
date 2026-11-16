package com.guicedee.vertx.grpc;

import com.guicedee.client.utils.Discovery;


import com.guicedee.client.Environment;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
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

        for (var annotation : Discovery.annotations(GrpcOptions.class))
            processAnnotations(new GrpcOptions[]{annotation});

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

    /** Resolves the normal option key before interpreting the annotation fallback. */
    private static String resolve(String annotationValue, String envKey, String fallback) {
        return Environment.getSystemPropertyOrEnvironment(envKey,
                annotationValue != null && !annotationValue.isBlank() ? annotationValue : fallback);
    }

    /** Resolves explicit placeholders or a bare configuration name in annotation text. */
    public static String resolveEnvPlaceholders(String value) {
        return com.guicedee.client.Environment.resolveAnnotationValue(value);
    }

    @Override
    public Integer sortOrder() {
        return Integer.MIN_VALUE + 62;
    }
}




