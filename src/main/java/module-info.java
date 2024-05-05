import com.guicedee.guicedinjection.interfaces.IGuiceModule;
import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.guicedinjection.interfaces.IGuicePreStartup;
import com.guicedee.vertx.VertXModule;
import com.guicedee.vertx.VertXPostStartup;

module guiced.vertx {
    requires io.vertx;

    requires com.guicedee.client;
    requires com.guicedee.jsonrepresentation;

    requires static lombok;
    exports com.guicedee.vertx.spi;

    opens com.guicedee.vertx to com.google.guice;

    provides IGuicePreStartup with VertXPostStartup;
    provides IGuicePreDestroy with VertXPostStartup;
    provides IGuiceModule with VertXModule;

    uses com.guicedee.vertx.spi.VertxConfigurator;
    uses com.guicedee.vertx.spi.VertxRouterConfigurator;
    uses com.guicedee.vertx.spi.VertxHttpServerConfigurator;
    uses com.guicedee.vertx.spi.VertxHttpServerOptionsConfigurator;
}