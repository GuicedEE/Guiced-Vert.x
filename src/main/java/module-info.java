import com.guicedee.guicedinjection.interfaces.IGuiceModule;
import com.guicedee.guicedinjection.interfaces.IGuicePostStartup;
import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.guicedinjection.interfaces.IGuicePreStartup;
import com.guicedee.vertx.VertXModule;
import com.guicedee.vertx.VertXPostStartup;
import com.guicedee.vertx.spi.VertXPreStartup;

module com.guicedee.vertx {
    requires transitive io.vertx.core;
    requires transitive io.vertx.web;

    requires transitive com.guicedee.client;
    requires transitive com.guicedee.jsonrepresentation;

    requires static lombok;
    exports com.guicedee.vertx.spi;

    opens com.guicedee.vertx to com.google.guice;
    opens com.guicedee.vertx.spi to com.google.guice;

    provides IGuicePreStartup with VertXPreStartup;
    provides IGuicePostStartup with VertXPostStartup;
    provides IGuicePreDestroy with VertXPostStartup;
    provides IGuiceModule with VertXModule;

    uses com.guicedee.vertx.spi.VertxConfigurator;
    uses com.guicedee.vertx.spi.VertxRouterConfigurator;
    uses com.guicedee.vertx.spi.VertxHttpServerConfigurator;
    uses com.guicedee.vertx.spi.VertxHttpServerOptionsConfigurator;
}