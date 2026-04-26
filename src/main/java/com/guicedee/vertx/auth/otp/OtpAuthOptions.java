package com.guicedee.vertx.auth.otp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares OTP (One-Time Password) authentication configuration.
 * Place on a class or {@code package-info.java} to opt-in to OTP authentication.
 * <p>
 * Supports both HOTP (counter-based) and TOTP (time-based) modes.
 * The provider requires an {@link IOtpAuthenticatorService} SPI implementation
 * to fetch and update authenticator state from your storage backend.
 * <p>
 * All attributes can be overridden via environment variables with the
 * {@code VERTX_AUTH_OTP_} prefix.
 *
 * <h3>Usage — TOTP (Google Authenticator compatible)</h3>
 * <pre>
 * &#64;OtpAuthOptions(
 *     type = OtpType.TOTP,
 *     passwordLength = 6,
 *     period = 30
 * )
 * package com.example.auth;
 * </pre>
 *
 * <h3>Usage — HOTP (counter-based)</h3>
 * <pre>
 * &#64;OtpAuthOptions(
 *     type = OtpType.HOTP,
 *     passwordLength = 6,
 *     lookAheadWindow = 5,
 *     authAttemptsLimit = 10
 * )
 * package com.example.auth;
 * </pre>
 *
 * @see IOtpAuthenticatorService
 * @see OtpType
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface OtpAuthOptions
{
    /**
     * @return The OTP type: {@link OtpType#TOTP} (time-based) or {@link OtpType#HOTP} (counter-based).
     *         Default is TOTP.
     */
    OtpType type() default OtpType.TOTP;

    /**
     * @return OTP code length. Valid values: 6, 7, or 8. Default 6.
     */
    int passwordLength() default 6;

    /**
     * @return Maximum number of failed authentication attempts before lockout.
     *         0 = unlimited (no limit). Default 0.
     */
    int authAttemptsLimit() default 0;

    // ── TOTP-specific ───────────────────────────────────

    /**
     * @return Time period in seconds during which a TOTP code is valid.
     *         Default 30. Only used when {@link #type()} is {@link OtpType#TOTP}.
     */
    int period() default 30;

    // ── HOTP-specific ───────────────────────────────────

    /**
     * @return Look-ahead window size for HOTP resynchronization.
     *         0 = disabled. Only used when {@link #type()} is {@link OtpType#HOTP}.
     */
    int lookAheadWindow() default 0;

    /**
     * @return Initial counter value for HOTP.
     *         Only used when {@link #type()} is {@link OtpType#HOTP}. Default 0.
     */
    long counter() default 0;
}

