package com.guicedee.vertx.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.proxy.ProxyConnectionInfo;
import com.guicedee.vertx.proxy.ProxyModule;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.httpproxy.HttpProxy;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the HTTP Proxy module.
 * <p>
 * Starts a simple origin HTTP server, then configures a {@link ProxyModule}
 * to proxy requests to it. Verifies that requests to the proxy port are
 * correctly forwarded to the origin and responses returned.
 */
@Log4j2
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HttpProxyTest {

    private static final int ORIGIN_PORT = 19876;
    private static final int PROXY_PORT = 19877;
    private static final String ORIGIN_RESPONSE = "Hello from origin!";

    private static HttpServer originServer;

    @BeforeAll
    static void setUp() throws Exception {
        // Boot the test context (starts Vert.x)
        IGuiceContext.modules.add(new TestProxyModule());
        IGuiceContext.instance().inject();

        // Start a simple origin server
        Vertx vertx = VertXPreStartup.getVertx();
        CompletableFuture<Void> started = new CompletableFuture<>();
        originServer = vertx.createHttpServer();
        originServer.requestHandler(req -> {
            log.info("Origin received: {} {}", req.method(), req.uri());
            req.response()
                    .putHeader("Content-Type", "text/plain")
                    .end(ORIGIN_RESPONSE);
        });
        originServer.listen(ORIGIN_PORT, "127.0.0.1")
                .onSuccess(s -> {
                    log.info("✅ Origin server listening on port {}", s.actualPort());
                    started.complete(null);
                })
                .onFailure(started::completeExceptionally);
        started.get(5, TimeUnit.SECONDS);

        // Give proxy server a moment to start
        Thread.sleep(500);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (originServer != null) {
            originServer.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        IGuiceContext.instance().destroy();
    }

    @Test
    @Order(1)
    void testProxyForwardsRequest() throws Exception {
        Vertx vertx = VertXPreStartup.getVertx();
        HttpClient client = vertx.createHttpClient();

        CompletableFuture<String> responseFuture = new CompletableFuture<>();

        client.request(HttpMethod.GET, PROXY_PORT, "127.0.0.1", "/test-path")
                .compose(HttpClientRequest::send)
                .compose(resp -> {
                    assertEquals(200, resp.statusCode(), "Proxy should return 200");
                    return resp.body();
                })
                .onSuccess(body -> responseFuture.complete(body.toString()))
                .onFailure(responseFuture::completeExceptionally);

        String body = responseFuture.get(5, TimeUnit.SECONDS);
        assertEquals(ORIGIN_RESPONSE, body, "Proxy should forward the origin response");
        log.info("✅ Proxy test passed — received: {}", body);
    }

    @Test
    @Order(2)
    void testProxyBindingAvailable() {
        HttpProxy proxy = IGuiceContext.get(HttpProxy.class);
        assertNotNull(proxy, "HttpProxy should be bound in Guice");
    }

    /**
     * Test proxy module that proxies from PROXY_PORT to ORIGIN_PORT.
     */
    public static class TestProxyModule extends ProxyModule<TestProxyModule> {
        @Override
        protected ProxyConnectionInfo getProxyConnectionInfo() {
            return new ProxyConnectionInfo()
                    .setName("test-proxy")
                    .setProxyHost("127.0.0.1")
                    .setProxyPort(PROXY_PORT)
                    .setOriginHost("127.0.0.1")
                    .setOriginPort(ORIGIN_PORT)
                    .setDefaultConnection(true);
        }
    }
}

