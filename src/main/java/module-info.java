import com.guicedee.vertx.VertXModule;
import com.guicedee.vertx.VertXPostStartup;
import com.guicedee.vertx.VertXVerticalPreStartup;
import com.guicedee.vertx.spi.*;
import com.guicedee.guicedinjection.interfaces.*;

module com.guicedee.vertx {
    requires transitive io.vertx.core;
    requires transitive io.vertx.web;

    requires transitive com.guicedee.client;
    requires transitive com.guicedee.jsonrepresentation;

    requires transitive org.apache.logging.log4j;

    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.core;

    requires static lombok;
    exports com.guicedee.vertx.spi;

    opens com.guicedee.vertx to com.google.guice;
    opens com.guicedee.vertx.spi to com.google.guice;

    provides IGuicePreStartup with VertXPreStartup,VertXVerticalPreStartup;
    provides IGuicePostStartup with VertXPostStartup;
    provides IGuicePreDestroy with VertXPostStartup;
    provides IGuiceModule with VertXModule;

    uses com.guicedee.vertx.spi.VertxConfigurator;
    uses VerticleStartup;
}