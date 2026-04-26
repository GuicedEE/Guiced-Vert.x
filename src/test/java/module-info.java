open module guiced.vertx.test {
    requires com.google.guice;

    requires com.guicedee.client;
    requires com.guicedee.vertx;

    requires io.vertx.core;
    requires io.vertx.auth.common;
    requires io.vertx.auth.jwt;
    requires io.vertx.auth.properties;
    requires io.vertx.auth.abac;
    requires io.vertx.auth.htpasswd;
    requires io.vertx.auth.otp;

    requires io.github.classgraph;
    requires org.apache.logging.log4j;
    requires static lombok;
    requires org.junit.jupiter.api;

    exports com.guicedee.vertx.test;
    exports com.guicedee.vertx.spi.test;

    provides com.guicedee.client.services.IGuiceProvider
            with com.guicedee.vertx.test.TestGuiceProvider;
}
