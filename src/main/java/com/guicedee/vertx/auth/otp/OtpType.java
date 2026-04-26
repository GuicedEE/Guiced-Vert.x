package com.guicedee.vertx.auth.otp;

/**
 * The type of OTP (One-Time Password) authentication to use.
 */
public enum OtpType
{
    /**
     * HOTP — HMAC-based One-Time Password (counter-based).
     * Each authentication increments a counter. Suitable for hardware tokens.
     */
    HOTP,

    /**
     * TOTP — Time-based One-Time Password.
     * Codes are valid for a configurable time period (default 30 seconds).
     * Compatible with Google Authenticator and similar apps.
     */
    TOTP
}

