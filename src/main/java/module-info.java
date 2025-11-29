import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import com.guicedee.vertx.*;
import com.guicedee.vertx.spi.*;

module com.guicedee.vertx {
    requires transitive io.vertx.core;

    requires transitive com.guicedee.client;
    requires transitive com.guicedee.jsonrepresentation;

    requires transitive org.apache.logging.log4j;

    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.core;

    requires transitive io.smallrye.mutiny;
    requires transitive io.vertx.mutiny;

    requires jakarta.cdi;


    requires static lombok;

    exports com.guicedee.vertx.spi;
    exports com.guicedee.vertx;

    opens com.guicedee.vertx to com.google.guice;
    opens com.guicedee.vertx.spi to com.google.guice;

    provides IGuicePreStartup with VertXPreStartup;
    //provides IGuicePostStartup with VertXPostStartup;
    provides IGuicePreDestroy with VertXPostStartup;
    provides IGuiceModule with VertXModule;
    //provides io.vertx.core.spi.VertxThreadFactory with InterceptingThreadFactory;
    //provides io.vertx.core.spi.VertxThreadFactory with CallScopeAwareVertxThreadFactory;

    uses com.guicedee.vertx.spi.VertxConfigurator;
    uses VerticleStartup;
}