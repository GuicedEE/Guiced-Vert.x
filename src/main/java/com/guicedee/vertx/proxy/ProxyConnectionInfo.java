package com.guicedee.vertx.proxy;

import com.fasterxml.jackson.annotation.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Configuration holder for a Vert.x HTTP reverse proxy.
 * <p>
 * Supports environment variable resolution for all connection properties.
 * The proxy listens on {@link #proxyHost}:{@link #proxyPort} and forwards
 * requests to {@link #originHost}:{@link #originPort}.
 *
 * <h3>Environment Variable Pattern</h3>
 * <pre>
 * PROXY_{NAME}_HOST, PROXY_{NAME}_PORT,
 * PROXY_{NAME}_ORIGIN_HOST, PROXY_{NAME}_ORIGIN_PORT,
 * PROXY_{NAME}_ORIGIN_SSL, PROXY_{NAME}_CACHING,
 * PROXY_{NAME}_WEBSOCKET
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
public class ProxyConnectionInfo {

    /**
     * A logical name for this proxy (used as the Guice @Named qualifier).
     */
    private String name = "default";

    /**
     * The host the proxy server listens on. Defaults to {@code 0.0.0.0}.
     */
    private String proxyHost = "0.0.0.0";

    /**
     * The port the proxy server listens on. Defaults to {@code 8080}.
     */
    private int proxyPort = 8080;

    /**
     * The origin server host to forward requests to. Defaults to {@code localhost}.
     */
    private String originHost = "localhost";

    /**
     * The origin server port. Defaults to {@code 7070}.
     */
    private int originPort = 7070;

    /**
     * Whether the origin connection uses SSL/TLS. Defaults to {@code false}.
     */
    private boolean originSsl = false;

    /**
     * Whether to enable response caching. Defaults to {@code false}.
     */
    private boolean cachingEnabled = false;

    /**
     * Whether to support WebSocket upgrades. Defaults to {@code true}.
     */
    private boolean webSocketEnabled = true;

    /**
     * If this is the default HttpProxy binding (no @Named required).
     */
    private boolean defaultConnection = true;

    /**
     * Interceptor classes to instantiate via Guice and add to the proxy.
     * Each class must implement {@code io.vertx.httpproxy.ProxyInterceptor}.
     */
    private final List<Class<?>> interceptorClasses = new ArrayList<>();

    /**
     * Interceptor classes that should also handle WebSocket upgrade handshakes.
     */
    private final List<Class<?>> webSocketInterceptorClasses = new ArrayList<>();

    /**
     * Adds an interceptor class to be registered on the proxy.
     *
     * @param interceptorClass a class implementing ProxyInterceptor
     * @return this for fluent chaining
     */
    public ProxyConnectionInfo addInterceptor(Class<?> interceptorClass) {
        this.interceptorClasses.add(interceptorClass);
        return this;
    }

    /**
     * Adds an interceptor class that also participates in WebSocket upgrade handshakes.
     *
     * @param interceptorClass a class implementing ProxyInterceptor
     * @return this for fluent chaining
     */
    public ProxyConnectionInfo addWebSocketInterceptor(Class<?> interceptorClass) {
        this.webSocketInterceptorClasses.add(interceptorClass);
        return this;
    }
}


