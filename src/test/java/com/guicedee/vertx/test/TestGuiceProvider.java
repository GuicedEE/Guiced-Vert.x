package com.guicedee.vertx.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.client.services.IGuiceProvider;

/**
 * Test {@link IGuiceProvider} that supplies the lightweight {@link TestGuiceContext}.
 * <p>
 * Registered via {@code provides IGuiceProvider with TestGuiceProvider} in the
 * test {@code module-info.java} so that {@link IGuiceContext#getContext()} can
 * locate a provider without requiring the full {@code guice-injection} module.
 */
public class TestGuiceProvider implements IGuiceProvider {

    @Override
    public IGuiceContext get() {
        return TestGuiceContext.instance();
    }
}

