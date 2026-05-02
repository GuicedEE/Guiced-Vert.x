package com.guicedee.vertx.proxy;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.guicedee.client.IGuiceContext;
import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.cache.CacheOptions;
import lombok.extern.log4j.Log4j2;

/**
 * A Guice module that creates and binds a Vert.x {@link HttpProxy} reverse proxy into the injector.
 * <p>
 * <h3>Usage</h3>
 * <ol>
 *   <li>Subclass this class and implement {@link #getProxyConnectionInfo()}.</li>
 *   <li>Register the subclass as an {@code IGuiceModule} SPI provider.</li>
 *   <li>Inject {@code HttpProxy} (optionally with {@code @Named("yourName")}).</li>
 * </ol>
 *
 * <pre>{@code
 * public class MyProxyModule extends ProxyModule<MyProxyModule> {
 *     @Override
 *     protected ProxyConnectionInfo getProxyConnectionInfo() {
 *         return new ProxyConnectionInfo()
 *                 .setName("api-gateway")
 *                 .setProxyPort(8080)
 *                 .setOriginHost("api-server")
 *                 .setOriginPort(3000);
 *     }
 * }
 * }</pre>
 */
@Log4j2
public abstract class ProxyModule<J extends ProxyModule<J>>
        extends AbstractModule
        implements IGuiceModule<J>, IGuicePreDestroy<J> {

    private HttpProxy httpProxy;
    private HttpServer proxyServer;
    private HttpClient proxyClient;

    /**
     * Provides the proxy connection info for this module.
     *
     * @return the connection info describing how to configure the reverse proxy
     */
    protected abstract ProxyConnectionInfo getProxyConnectionInfo();

    @Override
    protected void configure() {
        ProxyConnectionInfo info = getProxyConnectionInfo();
        if (info == null) {
            log.error("❌ ProxyConnectionInfo returned null from {}", getClass().getName());
            return;
        }

        log.info("🔀 Configuring HTTP Proxy module '{}' — {}:{} → {}:{}",
                info.getName(), info.getProxyHost(), info.getProxyPort(),
                info.getOriginHost(), info.getOriginPort());

        try {
            Vertx vertx = VertXPreStartup.getVertx();
            if (vertx == null) {
                log.error("❌ Vert.x instance is not available. Cannot create HttpProxy.");
                return;
            }

            // Create the HTTP client for proxying to the origin
            proxyClient = vertx.createHttpClient();

            // Build proxy options
            ProxyOptions proxyOptions = new ProxyOptions();
            proxyOptions.setSupportWebSocket(info.isWebSocketEnabled());
            if (info.isCachingEnabled()) {
                proxyOptions.setCacheOptions(new CacheOptions());
            }

            // Create the reverse proxy
            httpProxy = HttpProxy.reverseProxy(proxyOptions, proxyClient);
            httpProxy.origin(info.getOriginPort(), info.getOriginHost());

            // Register interceptors via Guice
            for (Class<?> interceptorClass : info.getInterceptorClasses()) {
                try {
                    Object interceptor = IGuiceContext.get(interceptorClass);
                    if (interceptor instanceof ProxyInterceptor pi) {
                        httpProxy.addInterceptor(pi);
                        log.info("  ↳ Interceptor registered: {}", interceptorClass.getSimpleName());
                    } else {
                        log.warn("⚠️ Class {} does not implement ProxyInterceptor", interceptorClass.getName());
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to create interceptor {}: {}", interceptorClass.getName(), e.getMessage(), e);
                }
            }

            // Register WebSocket-aware interceptors (also invoked during WebSocket upgrades)
            for (Class<?> interceptorClass : info.getWebSocketInterceptorClasses()) {
                try {
                    Object interceptor = IGuiceContext.get(interceptorClass);
                    if (interceptor instanceof ProxyInterceptor pi) {
                        httpProxy.addInterceptor(pi, true);
                        log.info("  ↳ WebSocket interceptor registered: {}", interceptorClass.getSimpleName());
                    } else {
                        log.warn("⚠️ Class {} does not implement ProxyInterceptor", interceptorClass.getName());
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to create WebSocket interceptor {}: {}", interceptorClass.getName(), e.getMessage(), e);
                }
            }

            // Create and start the proxy server
            proxyServer = vertx.createHttpServer();
            proxyServer.requestHandler(httpProxy);
            proxyServer.listen(info.getProxyPort(), info.getProxyHost())
                    .onSuccess(s -> log.info("✅ HTTP Proxy '{}' listening on {}:{}", info.getName(), info.getProxyHost(), s.actualPort()))
                    .onFailure(t -> log.error("❌ HTTP Proxy '{}' failed to start: {}", info.getName(), t.getMessage(), t));

            // Bind HttpProxy with @Named qualifier
            bind(HttpProxy.class)
                    .annotatedWith(Names.named(info.getName()))
                    .toInstance(httpProxy);

            // If this is the default, also bind without @Named
            if (info.isDefaultConnection()) {
                bind(HttpProxy.class).toInstance(httpProxy);
            }

            log.info("✅ HttpProxy bound as @Named(\"{}\"){}",
                    info.getName(),
                    info.isDefaultConnection() ? " [default]" : "");

        } catch (Throwable t) {
            log.error("❌ Failed to create HttpProxy for '{}': {}", info.getName(), t.getMessage(), t);
        }
    }

    @Override
    public void onDestroy() {
        if (proxyServer != null) {
            try {
                proxyServer.close();
                log.info("🛑 Proxy server '{}' closed", getProxyConnectionInfo().getName());
            } catch (Throwable t) {
                log.debug("⚠️ Proxy server close failed: {}", t.getMessage());
            }
        }
        if (proxyClient != null) {
            try {
                proxyClient.close();
                log.info("🛑 Proxy client '{}' closed", getProxyConnectionInfo().getName());
            } catch (Throwable t) {
                log.debug("⚠️ Proxy client close failed: {}", t.getMessage());
            }
        }
    }

    @Override
    public Integer sortOrder() {
        return 60;
    }
}






