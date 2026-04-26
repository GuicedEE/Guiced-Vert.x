package com.guicedee.vertx.auth.abac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares ABAC (Attribute-Based Access Control) authorization configuration.
 * Place on a class or {@code package-info.java} to opt-in to policy-based authorization.
 * <p>
 * Policies determine which authorizations are granted to a user based on attribute matching.
 * Each policy contains attribute conditions (matched against the {@link io.vertx.ext.auth.User})
 * and a set of authorizations granted when all conditions match.
 * <p>
 * Policies can be provided in three ways (all additive):
 * <ol>
 *   <li><strong>Inline JSON</strong> — via {@link #policies()}</li>
 *   <li><strong>File paths</strong> — via {@link #policyFiles()} (JSON files, each containing a single policy or an array)</li>
 *   <li><strong>SPI</strong> — implement {@link IAbacPolicyProvider} and register via {@code module-info.java}</li>
 * </ol>
 * <p>
 * All string attributes support {@code ${ENV_VAR}} placeholders and
 * {@code VERTX_AUTH_ABAC_} prefixed environment variable overrides.
 *
 * <h3>Policy JSON format</h3>
 * <pre>
 * {
 *   "name": "Only MFA users have DELETE rights",
 *   "subjects": ["user-123"],
 *   "attributes": {
 *     "/principal/amr": { "eq": "mfa" }
 *   },
 *   "authorizations": [
 *     { "type": "wildcard", "permission": "web:DELETE" }
 *   ]
 * }
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 * &#64;AbacOptions(
 *     policyFiles = {"policies/admin.json", "policies/readonly.json"}
 * )
 * package com.example.auth;
 * </pre>
 *
 * <pre>
 * &#64;AbacOptions(
 *     policies = {
 *         "{\"name\":\"MFA DELETE\",\"attributes\":{\"/principal/amr\":{\"eq\":\"mfa\"}}," +
 *         "\"authorizations\":[{\"type\":\"wildcard\",\"permission\":\"web:DELETE\"}]}"
 *     }
 * )
 * package com.example.auth;
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface AbacOptions
{
    /**
     * @return Inline policy definitions as JSON strings.
     *         Each string must be a valid JSON object matching the Policy format.
     */
    String[] policies() default {};

    /**
     * @return Paths to JSON files containing policy definitions.
     *         Each file may contain a single policy JSON object or a JSON array of policies.
     *         Paths are resolved via the Vert.x filesystem (classpath or absolute).
     */
    String[] policyFiles() default {};
}

