package com.guicedee.vertx.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for configuring the Vert.x Address Resolver.
 *
 * <p>The Address Resolver manages DNS resolution for both local and external hostnames
 * using the system's `hosts` file and DNS queries (e.g., A, AAAA records).</p>
 *
 * <p>This annotation enables fine-grained control over hostname resolution behavior,
 * including caching, timeouts, and query characteristics.</p>
 *
 * <p>It is applicable at the package or type level and is retained at runtime
 * to allow for dynamic configuration adjustments.</p>
 *
 * Usage
 * <code>
 *     @AddressBusResolverOptions(
 *     hostsPath = "/etc/hosts",
 *     hostsRefreshPeriod = 30000,
 *     servers = {"8.8.8.8", "8.8.4.4"},
 *     rotateServers = true,
 *     cacheMinTimeToLive = 60,
 *     cacheMaxTimeToLive = 3600,
 *     cacheNegativeTimeToLive = 30,
 *     queryTimeout = 5000,
 *     maxQueries = 5,
 *     rdFlag = true,
 *     searchDomains = {"example.com", "sub.example.com"},
 *     ndots = 2,
 *     optResourceEnabled = true,
 *     roundRobinInetAddress = true
 * )
 * public class CustomAddressResolverConfig {
 *     // Application logic or configuration here
 * }
 * </code>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface AddressResolverOptions
{

    // HOSTS FILE CONFIGURATION

    /**
     * Sets the path to an alternate `hosts` configuration file to use instead of the default provided
     * by the operating system.
     *
     * @return the path to the hosts configuration file (default: system-defined)
     */
    String hostsPath() default "";

    /**
     * Sets how often (in milliseconds) the `hosts` file is refreshed. A value of 0 disables refreshing.
     *
     * @return the refresh period in milliseconds (default: 0, disabled)
     */
    int hostsRefreshPeriod() default 0;

    // DNS SERVERS CONFIGURATION

    /**
     * The list of DNS servers to use for hostname resolution. If null, the system's default resolver
     * configuration is used (e.g., `/etc/resolv.conf` on Linux or the system's DNS settings).
     *
     * @return an array of DNS server addresses (default: system-defined)
     */
    String[] servers() default {};

    /**
     * The flag for enabling DNS server rotation when multiple DNS servers are configured. If enabled,
     * queries are distributed across servers in a round-robin fashion.
     *
     * @return {@code true} if server rotation is enabled, {@code false} otherwise (default: false)
     */
    boolean rotateServers() default false;

    // CACHING

    /**
     * Sets the minimum Time-To-Live (TTL) for entries in the DNS cache.
     *
     * @return the minimum cache TTL in seconds (default: 0, no minimum)
     */
    int cacheMinTimeToLive() default 0;

    /**
     * Sets the maximum Time-To-Live (TTL) for entries in the DNS cache.
     *
     * @return the maximum cache TTL in seconds (default: {@code Integer.MAX_VALUE})
     */
    int cacheMaxTimeToLive() default Integer.MAX_VALUE;

    /**
     * Sets the TTL for negative DNS responses (e.g., unknown hosts).
     *
     * @return the TTL for negative cache entries in seconds (default: 0, no caching of negative responses)
     */
    int cacheNegativeTimeToLive() default 0;

    // DNS QUERIES

    /**
     * Sets the query timeout for DNS requests (in milliseconds).
     *
     * @return the query timeout in milliseconds (default: 5000 ms)
     */
    long queryTimeout() default 5000;

    /**
     * Sets the maximum number of DNS queries performed per resolution before failing.
     *
     * @return the maximum number of DNS queries (default: 4)
     */
    int maxQueries() default 4;

    /**
     * Enables or disables the "recursion desired" (RD) flag on DNS queries.
     *
     * @return {@code true} if recursion is desired, {@code false} otherwise (default: true)
     */
    boolean rdFlag() default true;

    // SEARCH DOMAINS

    /**
     * Sets the list of DNS search domains to use during hostname resolution.
     *
     * @return an array of search domains (default: system-defined)
     */
    String[] searchDomains() default {};

    /**
     * The `ndots` option specifies the minimum number of dots contained in a hostname before it
     * is treated as fully-qualified and DNS resolution is attempted without appending search domains.
     *
     * @return the `ndots` value (default: system-defined, typically 1 on non-Linux environments)
     */
    int ndots() default 1;

    // OTHER OPTIONS

    /**
     * Enables or disables the use of OPT resource records for extended DNS capabilities, such as EDNS0.
     *
     * @return {@code true} if OPT resources are enabled, {@code false} otherwise (default: false)
     */
    boolean optResourceEnabled() default false;

    /**
     * Enables or disables the round-robin selection of IP addresses when resolving a hostname
     * to multiple addresses.
     *
     * @return {@code true} if round-robin selection is enabled, {@code false} otherwise (default: false)
     */
    boolean roundRobinInetAddress() default false;
}