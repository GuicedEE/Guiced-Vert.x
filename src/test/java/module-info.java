open module guiced.vertx.test {
    requires com.google.guice;
    requires com.guicedee.client;
    requires com.guicedee.vertx;
    requires io.vertx.core;
    requires static lombok;
    requires org.junit.jupiter.api;

    exports com.guicedee.vertx.test;
    exports com.guicedee.vertx.spi.test;
}
