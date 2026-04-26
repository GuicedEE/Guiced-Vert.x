package com.guicedee.vertx.auth.otp;

import com.guicedee.client.Environment;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.auth.IGuicedAuthenticationProvider;
import io.github.classgraph.ScanResult;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ServiceLoader;
import java.util.Set;

/**
 * Auto-discovered OTP authentication provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when an {@link OtpAuthOptions} annotation is found
 * on a class or {@code package-info.java}. Creates either a {@link HotpAuth} or
 * {@link TotpAuth} instance based on the configured {@link OtpType}.
 * <p>
 * <strong>Requires</strong> an {@link IOtpAuthenticatorService} SPI implementation
 * to provide authenticator fetch/update callbacks for the storage backend.
 *
 * <h3>Environment variable overrides</h3>
 * <ul>
 *   <li>{@code VERTX_AUTH_OTP_TYPE} — {@code TOTP} or {@code HOTP}</li>
 *   <li>{@code VERTX_AUTH_OTP_PASSWORD_LENGTH} — 6, 7, or 8</li>
 *   <li>{@code VERTX_AUTH_OTP_AUTH_ATTEMPTS_LIMIT}</li>
 *   <li>{@code VERTX_AUTH_OTP_PERIOD} — TOTP period in seconds</li>
 *   <li>{@code VERTX_AUTH_OTP_LOOK_AHEAD_WINDOW} — HOTP resync window</li>
 *   <li>{@code VERTX_AUTH_OTP_COUNTER} — HOTP initial counter</li>
 * </ul>
 */
@Log4j2
public class OtpAuthenticationProvider implements IGuicedAuthenticationProvider
{
    @Getter
    private static AuthenticationProvider otpAuth;

    @Getter
    private static OtpAuthOptions otpAuthOptions;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = IGuiceContext.instance().getScanResult();
        otpAuthOptions = discoverOtpAuthOptions(scanResult);

        if (otpAuthOptions == null)
        {
            log.debug("No @OtpAuthOptions annotation found — OTP provider not activated");
            return null;
        }

        // Discover the authenticator service SPI
        IOtpAuthenticatorService authService = discoverAuthenticatorService();
        if (authService == null)
        {
            log.error("@OtpAuthOptions found but no IOtpAuthenticatorService SPI registered — " +
                    "OTP provider cannot function without authenticator fetch/update callbacks");
            return null;
        }

        try
        {
            OtpType type = OtpType.valueOf(env("TYPE", otpAuthOptions.type().name()));
            int passwordLength = Integer.parseInt(env("PASSWORD_LENGTH", String.valueOf(otpAuthOptions.passwordLength())));
            int authAttemptsLimit = Integer.parseInt(env("AUTH_ATTEMPTS_LIMIT", String.valueOf(otpAuthOptions.authAttemptsLimit())));

            otpAuth = switch (type)
            {
                case TOTP ->
                {
                    int period = Integer.parseInt(env("PERIOD", String.valueOf(otpAuthOptions.period())));
                    var opts = new io.vertx.ext.auth.otp.totp.TotpAuthOptions()
                            .setPasswordLength(passwordLength)
                            .setAuthAttemptsLimit(authAttemptsLimit)
                            .setPeriod(period);
                    var totp = io.vertx.ext.auth.otp.totp.TotpAuth.create(opts);
                    totp.authenticatorFetcher(authService.authenticatorFetcher());
                    totp.authenticatorUpdater(authService.authenticatorUpdater());
                    log.info("TOTP authentication provider created: passwordLength={}, period={}s, attemptsLimit={}",
                            passwordLength, period, authAttemptsLimit);
                    yield totp;
                }
                case HOTP ->
                {
                    int lookAheadWindow = Integer.parseInt(env("LOOK_AHEAD_WINDOW", String.valueOf(otpAuthOptions.lookAheadWindow())));
                    long counter = Long.parseLong(env("COUNTER", String.valueOf(otpAuthOptions.counter())));
                    var opts = new io.vertx.ext.auth.otp.hotp.HotpAuthOptions()
                            .setPasswordLength(passwordLength)
                            .setAuthAttemptsLimit(authAttemptsLimit)
                            .setLookAheadWindow(lookAheadWindow)
                            .setCounter(counter);
                    var hotp = io.vertx.ext.auth.otp.hotp.HotpAuth.create(opts);
                    hotp.authenticatorFetcher(authService.authenticatorFetcher());
                    hotp.authenticatorUpdater(authService.authenticatorUpdater());
                    log.info("HOTP authentication provider created: passwordLength={}, lookAheadWindow={}, counter={}, attemptsLimit={}",
                            passwordLength, lookAheadWindow, counter, authAttemptsLimit);
                    yield hotp;
                }
            };

            return otpAuth;
        }
        catch (Exception e)
        {
            log.error("Failed to create OTP authentication provider", e);
            return null;
        }
    }

    // ── SPI discovery ───────────────────────────────────

    private IOtpAuthenticatorService discoverAuthenticatorService()
    {
        @SuppressWarnings("rawtypes")
        Set<IOtpAuthenticatorService> services = IGuiceContext.instance()
                .getLoader(IOtpAuthenticatorService.class, true, ServiceLoader.load(IOtpAuthenticatorService.class));
        if (services.isEmpty())
        {
            return null;
        }
        IOtpAuthenticatorService service = services.iterator().next();
        log.info("Discovered IOtpAuthenticatorService: {}", service.getClass().getName());
        return service;
    }

    // ── Annotation discovery ────────────────────────────

    private OtpAuthOptions discoverOtpAuthOptions(ScanResult scanResult)
    {
        var annotatedClasses = scanResult.getClassesWithAnnotation(OtpAuthOptions.class);
        for (var ci : annotatedClasses)
        {
            var ann = ci.loadClass().getAnnotation(OtpAuthOptions.class);
            if (ann != null)
            {
                log.debug("Found @OtpAuthOptions on class: {}", ci.getName());
                return ann;
            }
        }

        var packageInfoList = scanResult.getPackageInfo();
        for (var pkgInfo : packageInfoList)
        {
            try
            {
                Class<?> pkgClass = Class.forName(pkgInfo.getName() + ".package-info");
                var ann = pkgClass.getAnnotation(OtpAuthOptions.class);
                if (ann != null)
                {
                    log.debug("Found @OtpAuthOptions on package: {}", pkgInfo.getName());
                    return ann;
                }
            }
            catch (ClassNotFoundException _) { }
        }

        return null;
    }

    // ── Env helpers ─────────────────────────────────────

    static String env(String property, String defaultValue)
    {
        String value = Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_OTP_" + property, null);
        if (value != null && !value.isBlank())
        {
            return value;
        }
        return defaultValue;
    }

    /**
     * Resets all state — for testing purposes.
     */
    public static void reset()
    {
        otpAuth = null;
        otpAuthOptions = null;
    }

    @Override
    public Integer sortOrder()
    {
        return 110;
    }
}

