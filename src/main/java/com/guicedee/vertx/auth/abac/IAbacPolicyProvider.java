package com.guicedee.vertx.auth.abac;

import com.guicedee.client.services.IDefaultService;
import io.vertx.ext.auth.abac.Policy;

import java.util.List;

/**
 * SPI for contributing ABAC policies programmatically.
 * <p>
 * Implementations are discovered via {@code ServiceLoader} and their policies
 * are added to the {@link io.vertx.ext.auth.abac.PolicyBasedAuthorizationProvider}
 * alongside any policies declared via {@link AbacOptions}.
 * <p>
 * Use this when policies need to be computed dynamically, loaded from a database,
 * or assembled with custom {@link io.vertx.ext.auth.abac.Attribute} matchers
 * (including {@code Attribute.create(Function<User, Boolean>)}).
 * <p>
 * Register in {@code module-info.java}:
 * <pre>
 * provides IAbacPolicyProvider with MyPolicyProvider;
 * </pre>
 */
public interface IAbacPolicyProvider extends IDefaultService<IAbacPolicyProvider>
{
    /**
     * Returns the policies to register.
     *
     * @return A list of ABAC policies. Must not be null but may be empty.
     */
    List<Policy> getPolicies();
}

