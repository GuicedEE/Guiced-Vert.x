package com.guicedee.vertx.grpc;

import com.fasterxml.jackson.annotation.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Configuration holder for a Vert.x gRPC server and client connection.
 * <p>
 * Supports environment variable resolution for all connection properties.
 *
 * <h3>Environment Variable Pattern</h3>
 * <pre>
 * GRPC_{NAME}_HOST, GRPC_{NAME}_PORT,
 * GRPC_{NAME}_TLS_ENABLED, GRPC_{NAME}_TLS_CERT_PATH,
 * GRPC_{NAME}_TLS_KEY_PATH, GRPC_{NAME}_MAX_MESSAGE_SIZE,
 * GRPC_{NAME}_DEADLINE_PROPAGATION, GRPC_{NAME}_SCHEDULE_DEADLINE,
 * GRPC_{NAME}_WEB_ENABLED, GRPC_{NAME}_TRANSCODING_ENABLED
 * </pre>
 */
@JsonAutoDetect(fieldVisibility = ANY, getterVisibility = NONE, setterVisibility = NONE)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
@EqualsAndHashCode(of = {"name"})
@Getter
@Setter
@Accessors(chain = true)
public class GrpcConnectionInfo {

    /**
     * A logical name for this gRPC configuration (used as the Guice @Named qualifier).
     */
    private String name = "default";

    /**
     * The host the gRPC server binds to. Defaults to {@code 0.0.0.0}.
     */
    private String host = "0.0.0.0";

    /**
     * The port the gRPC server listens on. Defaults to {@code 50051}.
     */
    private int port = 50051;

    /**
     * Whether TLS/SSL is enabled. Defaults to {@code false}.
     */
    private boolean tlsEnabled = false;

    /**
     * Path to the PEM certificate for TLS.
     */
    private String tlsCertPath;

    /**
     * Path to the PEM private key for TLS.
     */
    private String tlsKeyPath;

    /**
     * Maximum inbound message size in bytes. Defaults to 4MB.
     */
    private int maxMessageSize = 4194304;

    /**
     * Whether to propagate deadlines from incoming requests to outgoing client calls.
     */
    private boolean deadlinePropagation = false;

    /**
     * Whether to schedule deadlines automatically on the server.
     */
    private boolean scheduleDeadlineAutomatically = false;

    /**
     * Whether gRPC-Web protocol support is enabled. Defaults to {@code true}.
     */
    private boolean grpcWebEnabled = true;

    /**
     * Whether HTTP/JSON transcoding is enabled. Defaults to {@code true}.
     */
    private boolean transcodingEnabled = true;

    /**
     * If this is the default gRPC binding (no @Named required).
     */
    private boolean defaultConnection = true;
}

