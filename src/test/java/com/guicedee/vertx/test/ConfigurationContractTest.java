package com.guicedee.vertx.test;

import com.guicedee.client.Environment;
import com.guicedee.vertx.spi.*;
import com.guicedee.vertx.redis.*;
import com.guicedee.vertx.grpc.*;
import com.guicedee.vertx.auth.oauth2.OAuth2Options;
import com.guicedee.vertx.auth.oauth2.OAuth2AuthenticationProvider;
import io.vertx.core.VertxOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.ext.auth.oauth2.*;
import io.vertx.grpc.server.GrpcProtocol;
import io.vertx.grpc.server.GrpcServerOptions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationContractTest {
    @TempDir Path directory;
    Properties original;
    @BeforeEach void isolate() throws Exception {
        original = (Properties) System.getProperties().clone();
        System.getProperties().keySet().removeIf(k -> k.toString().startsWith("VERTX_") || k.toString().startsWith("REDIS_") || k.toString().startsWith("GRPC_"));
        System.setProperty("user.dir", directory.toString());
        System.setProperty("GUICEDEE_DISCOVERY_MODE", "spi");
        Files.writeString(directory.resolve(".env"), "CONFIG_SENTINEL=true\n");
        Files.writeString(directory.resolve(".env.local"), "CONFIG_SENTINEL=true\n");
        Environment.reloadDotEnv();
    }
    @AfterEach void restore() { System.setProperties(original); Environment.reloadDotEnv(); }

    @SuppressWarnings("unchecked")
    private static <A extends Annotation> A annotation(Class<A> type, Map<String, Object> values) {
        return (A) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) ->
                m.getName().equals("annotationType") ? type : values.getOrDefault(m.getName(), m.getDefaultValue()));
    }
    private static Object invoke(Class<?> type, Object target, String method, Class<?>[] signature, Object... args) throws Exception {
        var m = type.getDeclaredMethod(method, signature); m.setAccessible(true); return m.invoke(target, args);
    }
    private VertxOptions configured() throws Exception {
        return (VertxOptions) invoke(VertXPreStartup.class, new VertXPreStartup(), "configureVertxOptions", new Class<?>[0]);
    }

    @Test void environmentOnlyGroupsAndTimeUnitsReachUpstreamOptions() throws Exception {
        Files.writeString(directory.resolve(".env"), "VERTX_EVENTBUS_PORT=19000\nVERTX_ADDR_RESOLVER_HOSTS_REFRESH_PERIOD=30000\nVERTX_FILESYSTEM_FILE_CACHING=false\nVERTX_METRICS_ENABLED=true\nVERTX_USE_DAEMON_THREADS=true\nVERTX_WARNING_EXCEPTION_TIME=7\nVERTX_WARNING_EXCEPTION_TIME_UNIT=SECONDS\n");
        Environment.reloadDotEnv();
        var options = configured();
        assertEquals(19000, options.getEventBusOptions().getPort());
        assertEquals(30000, options.getAddressResolverOptions().getHostsRefreshPeriod());
        assertEquals(TimeUnit.MILLISECONDS, options.getAddressResolverOptions().getHostsRefreshPeriodUnit());
        assertFalse(options.getFileSystemOptions().isFileCachingEnabled());
        assertTrue(options.getMetricsOptions().isEnabled());
        assertTrue(options.getUseDaemonThread());
        assertEquals(7, options.getWarningExceptionTime());
        assertEquals(TimeUnit.SECONDS, options.getWarningExceptionTimeUnit());
    }

    @Test void annotationDefaultsMatchUpstreamDurationsAndFlags() throws Exception {
        var expected = new VertxOptions();
        var ann = annotation(VertX.class, Map.of("eventLoopPoolSize", expected.getEventLoopPoolSize()));
        var actual = new VertxOptions();
        invoke(VertXPreStartup.class, new VertXPreStartup(), "applyVertxAnnotation", new Class<?>[]{VertxOptions.class, VertX.class}, actual, ann);
        assertEquals(expected.getMaxWorkerExecuteTimeUnit().toNanos(expected.getMaxWorkerExecuteTime()), actual.getMaxWorkerExecuteTimeUnit().toNanos(actual.getMaxWorkerExecuteTime()));
        assertEquals(expected.getMaxEventLoopExecuteTimeUnit().toNanos(expected.getMaxEventLoopExecuteTime()), actual.getMaxEventLoopExecuteTimeUnit().toNanos(actual.getMaxEventLoopExecuteTime()));
        assertEquals(expected.getWarningExceptionTimeUnit().toNanos(expected.getWarningExceptionTime()), actual.getWarningExceptionTimeUnit().toNanos(actual.getWarningExceptionTime()));
        assertEquals(expected.getQuorumSize(), actual.getQuorumSize());
        assertEquals(expected.getUseDaemonThread(), actual.getUseDaemonThread());
        assertEquals(expected.getDisableTCCL(), actual.getDisableTCCL());
        assertEquals(expected.getPreferNativeTransport(), actual.getPreferNativeTransport());
    }

    @Test void namedWorkerTimeAndUnitAreAppliedTogether() throws Exception {
        var ann = annotation(Verticle.class, Map.of("value", "jobs", "maxWorkerExecuteTime", 3L, "maxWorkerExecuteTimeUnit", TimeUnit.MINUTES));
        var options = (DeploymentOptions) invoke(VerticleBuilder.class, new VerticleBuilder(), "toDeploymentOptions", new Class<?>[]{Verticle.class}, ann);
        assertEquals("jobs", options.getWorkerPoolName());
        assertEquals(TimeUnit.MINUTES.toNanos(3), options.getMaxWorkerExecuteTimeUnit().toNanos(options.getMaxWorkerExecuteTime()));
    }

    @Test void perAddressSourcesPrecedeGlobalAndDefaultsDoNotLeak() throws Exception {
        Files.writeString(directory.resolve(".env"), "VERTX_EVENT_WORKER_ORDERS_CREATED=true\n");
        Environment.reloadDotEnv();
        System.setProperty("VERTX_EVENT_WORKER", "false");
        assertEquals("true", eventOption("orders.created", "VERTX_EVENT_WORKER", "false"));
        assertEquals("false", eventOption("other", "VERTX_EVENT_WORKER", "true"));
        System.clearProperty("VERTX_EVENT_WORKER");
        assertEquals("first", eventOption("other", "VERTX_EVENT_WORKER_POOL", "first"));
        assertEquals("second", eventOption("other", "VERTX_EVENT_WORKER_POOL", "second"));
        System.setProperty("VERTX_EVENT_WORKER_ORDERS_CREATED", "false");
        assertEquals("false", eventOption("orders.created", "VERTX_EVENT_WORKER", "true"));
    }
    private String eventOption(String address, String key, String fallback) throws Exception {
        return (String) invoke(VertxEventRegistry.class, null, "resolveOption", new Class<?>[]{String.class, String.class, String.class}, address, key, fallback);
    }

    @Test void bareAnnotationReferencesReachConnectionAndDeploymentSettings() throws Exception {
        System.setProperty("CONFIG_CONTRACT_REDIS_URI", "redis://localhost:6381/5");
        System.setProperty("CONFIG_CONTRACT_REDIS_REPLICA", "redis://localhost:6382/5");
        var redis = annotation(RedisOptions.class, Map.of("connectionString", "CONFIG_CONTRACT_REDIS_URI",
                "endpoints", new String[]{"CONFIG_CONTRACT_REDIS_REPLICA"}));
        var connection = RedisPreStartup.buildFromAnnotation(redis);
        assertEquals("redis://localhost:6381/5", connection.getConnectionString());
        assertEquals(List.of("redis://localhost:6382/5"), connection.getEndpoints());
        System.setProperty("REDIS_CONNECTION_STRING", "redis://normal-override:6380");
        assertEquals("redis://normal-override:6380", RedisPreStartup.buildFromAnnotation(redis).getConnectionString());

        System.setProperty("CONFIG_CONTRACT_BIND_HOST", "127.0.0.2");
        var grpc = annotation(GrpcOptions.class, Map.of("host", "CONFIG_CONTRACT_BIND_HOST"));
        assertEquals("127.0.0.2", GrpcPreStartup.buildFromAnnotation(grpc).getHost());
        System.setProperty("GRPC_HOST", "CONFIG_CONTRACT_BIND_HOST");
        assertEquals("CONFIG_CONTRACT_BIND_HOST", GrpcPreStartup.buildFromAnnotation(grpc).getHost());

        System.setProperty("CONFIG_CONTRACT_POOL", "resolved-pool");
        var worker = annotation(Verticle.class, Map.of("value", "CONFIG_CONTRACT_POOL"));
        var deployment = (DeploymentOptions) invoke(VerticleBuilder.class, new VerticleBuilder(), "toDeploymentOptions", new Class<?>[]{Verticle.class}, worker);
        assertEquals("resolved-pool", deployment.getWorkerPoolName());
        assertEquals("resolved-pool", eventOption("orders.created", "VERTX_EVENT_WORKER_POOL", "CONFIG_CONTRACT_POOL"));
    }

    @Test void jwtAudienceEntriesAndOauthSiteResolveBareReferences() throws Exception {
        System.setProperty("CONFIG_CONTRACT_AUDIENCE", "audience-one");
        System.setProperty("CONFIG_CONTRACT_ISSUER", "https://issuer.test");
        var jwt = annotation(com.guicedee.vertx.auth.jwt.JwtAuthOptions.class, Map.of(
                "audience", new String[]{"CONFIG_CONTRACT_AUDIENCE", "audience-two"}, "issuer", "CONFIG_CONTRACT_ISSUER"));
        var signature = new Class<?>[]{com.guicedee.vertx.auth.jwt.JwtAuthOptions.class, io.vertx.core.Vertx.class};
        var provider = new com.guicedee.vertx.auth.jwt.JwtAuthenticationProvider();
        var options = (io.vertx.ext.auth.jwt.JWTAuthOptions) invoke(provider.getClass(), provider,
                "buildVertxJwtAuthOptions", signature, jwt, null);
        assertEquals(List.of("audience-one", "audience-two"), options.getJWTOptions().getAudience());
        assertEquals("https://issuer.test", options.getJWTOptions().getIssuer());
        System.setProperty("VERTX_AUTH_JWT_AUDIENCE", "CONFIG_CONTRACT_AUDIENCE");
        options = (io.vertx.ext.auth.jwt.JWTAuthOptions) invoke(provider.getClass(), provider,
                "buildVertxJwtAuthOptions", signature, jwt, null);
        assertEquals(List.of("CONFIG_CONTRACT_AUDIENCE"), options.getJWTOptions().getAudience());

        var oauth = annotation(OAuth2Options.class, Map.of("site", "CONFIG_CONTRACT_ISSUER"));
        var oauthOptions = (io.vertx.ext.auth.oauth2.OAuth2Options) invoke(OAuth2AuthenticationProvider.class,
                new OAuth2AuthenticationProvider(), "buildVertxOAuth2Options",
                new Class<?>[]{OAuth2Options.class, String.class, String.class}, oauth, "client", "secret");
        assertEquals("https://issuer.test", oauthOptions.getSite());
    }

    @Test void redisTlsAndPlaceholdersRespectDefaultsAndOverrides() {
        var ann = annotation(RedisOptions.class, Map.of("tlsEnabled", true, "connectionString", "${CONFIG_UNSET_URI:-redis://localhost:6380/4}"));
        assertTrue(RedisPreStartup.buildFromAnnotation(ann).isTlsEnabled());
        assertEquals("redis://localhost:6380/4", RedisPreStartup.buildFromAnnotation(ann).getConnectionString());
        System.setProperty("REDIS_TLS_ENABLED", "false");
        assertFalse(RedisPreStartup.buildFromAnnotation(ann).isTlsEnabled());
        assertEquals("fallback", GrpcPreStartup.resolveEnvPlaceholders("${CONFIG_UNSET_FALLBACK:fallback}"));
    }

    @Test void redisDatabasePreservesEscapedCredentialsAndSupportsUnixSockets() throws Exception {
        var signature = new Class<?>[]{String.class, int.class};
        String uri = "rediss://user:p%40ss@localhost:6380/4?user=other&db=9";
        assertEquals(uri, invoke(RedisModule.class, null, "withDatabase", signature, uri, -1));
        assertEquals("rediss://user:p%40ss@localhost:6380/2?user=other", invoke(RedisModule.class, null, "withDatabase", signature, uri, 2));
        assertEquals("unix:///tmp/redis.sock?password=p%40ss&db=3", invoke(RedisModule.class, null, "withDatabase", signature, "unix:///tmp/redis.sock?password=p%40ss&db=4", 3));
    }

    @Test void grpcLimitsAndProtocolsReachServerOptions() throws Exception {
        var info = new GrpcConnectionInfo().setMaxMessageSize(12345).setGrpcWebEnabled(false).setTranscodingEnabled(false);
        var options = (GrpcServerOptions) invoke(GrpcModule.class, null, "serverOptions", new Class<?>[]{GrpcConnectionInfo.class}, info);
        assertEquals(12345, options.getMaxMessageSize());
        assertEquals(Set.of(GrpcProtocol.HTTP_2), options.getEnabledProtocols());
    }

    @Test void grpcListenerEnforcesMessageLimitAndRejectsDisabledProtocols() throws Exception {
        var info = new GrpcConnectionInfo().setMaxMessageSize(16).setGrpcWebEnabled(false).setTranscodingEnabled(false);
        var options = (GrpcServerOptions) invoke(GrpcModule.class, null, "serverOptions", new Class<?>[]{GrpcConnectionInfo.class}, info);
        var vertx = io.vertx.core.Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1));
        var client = io.vertx.grpc.client.GrpcClient.client(vertx);
        var http = vertx.createHttpClient();
        try {
            var grpc = io.vertx.grpc.server.GrpcServer.server(vertx, options)
                    .callHandler(request -> request.handler(message -> request.response().end(message)));
            var server = await(vertx.createHttpServer().requestHandler(grpc).listen(0, "127.0.0.1"));
            var address = io.vertx.core.net.SocketAddress.inetSocketAddress(server.actualPort(), "127.0.0.1");
            var small = await(client.request(address)).fullMethodName("/contract.Echo/echo").timeout(5, TimeUnit.SECONDS);
            var response = await(small.send(io.vertx.core.buffer.Buffer.buffer("small")));
            assertEquals("small", await(response.last()).toString());
            assertEquals(io.vertx.grpc.common.GrpcStatus.OK, response.status());
            var large = await(client.request(address)).fullMethodName("/contract.Echo/echo").timeout(5, TimeUnit.SECONDS);
            var rejected = await(large.send(io.vertx.core.buffer.Buffer.buffer(new byte[32])));
            await(rejected.end().recover(failure -> io.vertx.core.Future.succeededFuture()));
            assertEquals(io.vertx.grpc.common.GrpcStatus.RESOURCE_EXHAUSTED, rejected.status());
            for (String contentType : List.of("application/grpc-web+proto", "application/json")) {
                var request = await(http.request(new io.vertx.core.http.RequestOptions().setHost("127.0.0.1")
                        .setPort(server.actualPort()).setURI("/contract.Echo/echo").setMethod(io.vertx.core.http.HttpMethod.POST)));
                assertEquals(415, await(request.putHeader("content-type", contentType).send()).statusCode());
            }
        } finally {
            await(client.close());
            await(http.close());
            await(vertx.close());
        }
    }
    private static <T> T await(io.vertx.core.Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test void oauthMillisecondIdleTimeoutAndGlobalLeewayArePreserved() throws Exception {
        System.setProperty("VERTX_AUTH_LEEWAY", "7");
        var ann = annotation(OAuth2Options.class, Map.of("readTimeout", 250));
        var options = (io.vertx.ext.auth.oauth2.OAuth2Options) invoke(OAuth2AuthenticationProvider.class, new OAuth2AuthenticationProvider(), "buildVertxOAuth2Options", new Class<?>[]{OAuth2Options.class, String.class, String.class}, ann, "client", "secret");
        assertEquals(250, options.getHttpClientOptions().getIdleTimeout());
        assertEquals(TimeUnit.MILLISECONDS, options.getHttpClientOptions().getIdleTimeoutUnit());
        assertEquals(7, options.getJWTOptions().getLeeway());
        System.setProperty("VERTX_AUTH_OAUTH2_JWT_LEEWAY", "0");
        options = (io.vertx.ext.auth.oauth2.OAuth2Options) invoke(OAuth2AuthenticationProvider.class, new OAuth2AuthenticationProvider(), "buildVertxOAuth2Options", new Class<?>[]{OAuth2Options.class, String.class, String.class}, ann, "client", "secret");
        assertEquals(0, options.getJWTOptions().getLeeway());
    }

    @Test void oauthDefaultsReachAuthorizationAndTokenRequestsWithoutMutatingCallers() throws Exception {
        var captured = new ArrayList<Object>();
        var delegate = (OAuth2Auth) Proxy.newProxyInstance(OAuth2Auth.class.getClassLoader(), new Class<?>[]{OAuth2Auth.class}, (p,m,a) -> {
            captured.add(a[0]);
            return m.getName().equals("authorizeURL") ? "url" : io.vertx.core.Future.succeededFuture();
        });
        var type = Class.forName("com.guicedee.vertx.auth.oauth2.ConfiguredOAuth2Auth");
        var constructor = type.getDeclaredConstructor(OAuth2Auth.class, String.class, String.class); constructor.setAccessible(true);
        var auth = (OAuth2Auth) constructor.newInstance(delegate, "openid, profile", "https://example.test/callback");
        var request = new OAuth2AuthorizationURL().setState("state");
        auth.authorizeURL(request);
        var resolved = (OAuth2AuthorizationURL) captured.getLast();
        assertEquals(List.of("openid", "profile"), resolved.getScopes());
        assertEquals("https://example.test/callback", resolved.getRedirectUri());
        assertEquals("state", resolved.getState());
        assertNull(request.getScopes());
        var credentials = new Oauth2Credentials().setCode("code");
        auth.authenticate(credentials);
        assertEquals("https://example.test/callback", ((Oauth2Credentials) captured.getLast()).getRedirectUri());
        assertNull(credentials.getRedirectUri());
        auth.authorizeURL(new OAuth2AuthorizationURL().setScopes(List.of()).setRedirectUri("https://explicit.test"));
        assertEquals(List.of(), ((OAuth2AuthorizationURL) captured.getLast()).getScopes());
        assertEquals("https://explicit.test", ((OAuth2AuthorizationURL) captured.getLast()).getRedirectUri());
    }
}
