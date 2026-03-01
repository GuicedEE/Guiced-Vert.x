open module guiced.vertx.test {
    requires com.google.guice;

    requires com.guicedee.client;
    requires com.guicedee.vertx;

    requires io.vertx.core;
    requires io.github.classgraph;
    requires static lombok;
    requires org.junit.jupiter.api;

    exports com.guicedee.vertx.test;
    exports com.guicedee.vertx.spi.test;

    provides com.guicedee.client.services.IGuiceProvider
            with com.guicedee.vertx.test.TestGuiceProvider;
}
