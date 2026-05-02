package com.guicedee.vertx.grpc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares gRPC server/client configuration for the application.
 * Place on a class or {@code package-info.java} to configure a gRPC endpoint.
 * <p>
 * All string attributes support {@code ${ENV_VAR}} placeholders resolved at startup,
 * and every attribute can be overridden via environment variables with the
 * {@code GRPC_} prefix (e.g. {@code GRPC_HOST}, {@code GRPC_PORT}).
 * <p>
 * Multiple gRPC configurations can be declared by repeating this annotation
 * (using {@link GrpcOptionsContainer}).
 *
 * <h2>Environment Variables</h2>
 * <ul>
 *   <li>{@code GRPC_HOST} — the host the gRPC server binds to</li>
 *   <li>{@code GRPC_PORT} — the port the gRPC server listens on</li>
 *   <li>{@code GRPC_TLS_ENABLED} — whether TLS is enabled</li>
 *   <li>{@code GRPC_TLS_CERT_PATH} — path to the PEM certificate</li>
 *   <li>{@code GRPC_TLS_KEY_PATH} — path to the PEM private key</li>
 *   <li>{@code GRPC_MAX_MESSAGE_SIZE} — maximum inbound message size in bytes</li>
 *   <li>{@code GRPC_DEADLINE_PROPAGATION} — whether to propagate deadlines</li>
 *   <li>{@code GRPC_SCHEDULE_DEADLINE} — whether to schedule deadlines automatically</li>
 *   <li>{@code GRPC_WEB_ENABLED} — whether gRPC-Web protocol is enabled</li>
 *   <li>{@code GRPC_TRANSCODING_ENABLED} — whether HTTP/JSON transcoding is enabled</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * &#64;GrpcOptions(
 *     name = "main",
 *     host = "0.0.0.0",
 *     port = 50051
 * )
 * package com.example.app;
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Repeatable(GrpcOptionsContainer.class)
public @interface GrpcOptions {

    /**
     * Logical name for this gRPC configuration (used as the Guice {@code @Named} qualifier).
     * Supports {@code ${ENV_VAR}} placeholders.
     */
    String name() default "default";

    /**
     * The host the gRPC server binds to. Defaults to {@code 0.0.0.0}.
     * Supports {@code ${ENV_VAR:default}} placeholders.
     */
    String host() default "${GRPC_HOST:0.0.0.0}";

    /**
     * The port the gRPC server listens on. Defaults to {@code 50051}.
     */
    int port() default 50051;

    /**
     * Whether TLS/SSL is enabled for the gRPC server.
     */
    boolean tlsEnabled() default false;

    /**
     * Path to the PEM certificate for TLS.
     * Supports {@code ${ENV_VAR}} placeholders.
     */
    String tlsCertPath() default "${GRPC_TLS_CERT_PATH:}";

    /**
     * Path to the PEM private key for TLS.
     * Supports {@code ${ENV_VAR}} placeholders.
     */
    String tlsKeyPath() default "${GRPC_TLS_KEY_PATH:}";

    /**
     * Maximum inbound message size in bytes. Defaults to 4MB (4194304).
     */
    int maxMessageSize() default 4194304;

    /**
     * Whether to propagate deadlines from incoming requests to outgoing gRPC client calls.
     */
    boolean deadlinePropagation() default false;

    /**
     * Whether to schedule deadlines automatically on the server when a timeout is received.
     */
    boolean scheduleDeadlineAutomatically() default false;

    /**
     * Whether gRPC-Web protocol support is enabled. Defaults to {@code true}.
     */
    boolean grpcWebEnabled() default true;

    /**
     * Whether HTTP/JSON transcoding is enabled. Defaults to {@code true}.
     */
    boolean transcodingEnabled() default true;

    /**
     * Whether this is the default gRPC binding (injectable without {@code @Named}).
     */
    boolean defaultConnection() default true;
}

