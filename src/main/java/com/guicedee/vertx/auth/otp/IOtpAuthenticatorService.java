package com.guicedee.vertx.auth.otp;

import com.guicedee.client.services.IDefaultService;
import io.vertx.core.Future;
import io.vertx.ext.auth.otp.Authenticator;

import java.util.function.Function;

/**
 * SPI for providing OTP authenticator storage operations.
 * <p>
 * Both HOTP and TOTP require a backend to fetch and update authenticator state
 * (identifier, key, counter, auth attempts). Implement this interface and register
 * via {@code module-info.java} to provide the storage callbacks.
 * <p>
 * <strong>This SPI is required</strong> when using {@link OtpAuthOptions}. Without it,
 * the OTP provider cannot authenticate users.
 *
 * <h3>Example</h3>
 * <pre>
 * public class MyOtpService implements IOtpAuthenticatorService {
 *
 *     &#64;Inject private Mutiny.SessionFactory sf;
 *
 *     &#64;Override
 *     public Function&lt;String, Future&lt;Authenticator&gt;&gt; authenticatorFetcher() {
 *         return id -&gt; sf.withSession(session -&gt;
 *             session.find(OtpEntity.class, id))
 *             .map(entity -&gt; entity != null ? entity.toAuthenticator() : null)
 *             .convert().toFuture();
 *     }
 *
 *     &#64;Override
 *     public Function&lt;Authenticator, Future&lt;Void&gt;&gt; authenticatorUpdater() {
 *         return auth -&gt; sf.withSession(session -&gt;
 *             session.merge(OtpEntity.from(auth)))
 *             .replaceWithVoid()
 *             .convert().toFuture();
 *     }
 * }
 * </pre>
 *
 * Register in {@code module-info.java}:
 * <pre>
 * provides IOtpAuthenticatorService with MyOtpService;
 * </pre>
 */
public interface IOtpAuthenticatorService extends IDefaultService<IOtpAuthenticatorService>
{
    /**
     * Returns a function that fetches an {@link Authenticator} by identifier.
     * <p>
     * The function receives the user identifier (String) and must return
     * a {@code Future<Authenticator>}. Return a succeeded future with {@code null}
     * if the identifier is not found.
     *
     * @return The fetcher function.
     */
    Function<String, Future<Authenticator>> authenticatorFetcher();

    /**
     * Returns a function that persists or updates an {@link Authenticator}.
     * <p>
     * For existing authenticators, at minimum the {@code counter} and
     * {@code authAttempts} fields must be updated. For new authenticators
     * (where {@link Authenticator#isRegistration()} is true), the entire
     * object must be persisted.
     *
     * @return The updater function.
     */
    Function<Authenticator, Future<Void>> authenticatorUpdater();
}

