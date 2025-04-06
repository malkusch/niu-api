package de.malkusch.niu;


import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

final class Authentication {

    private final String account;
    private final String password;
    private final String countryCode;
    private final Client client;
    private final static String APP_ID = "niu_8xt1afu6";

    public Authentication(String account, String password, String countryCode, Duration expirationWindow, Client client)
            throws IOException {

        this.account = assertNotEmpty(account, "account must not be empty");
        this.password = hashedPassword(assertNotEmpty(password, "password must not be empty"));
        this.countryCode = assertNotEmpty(countryCode, "countryCode must not be empty");
        this.expirationWindow = requireNonNull(expirationWindow);
        this.client = requireNonNull(client);

        refreshToken();
    }

    private static String hashedPassword(String password) {
        try {
            var md5 = MessageDigest.getInstance("MD5");
            md5.update(UTF_8.encode(password));
            return String.format("%032x", new BigInteger(1, md5.digest()));

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String assertNotEmpty(String value, String errorMessage) {
        requireNonNull(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

    record Token(String value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private volatile Token token;

    public Token token() throws IOException {
        if (token.isExpired()) {
            refreshToken();
        }
        return token;
    }

    private final Duration expirationWindow;
    private static final String LOGIN_URI = "https://account-fk.niu.com/v3/api/oauth2/token";
    private static Duration EXPIRES_AT_FALLBACK = Duration.ofHours(1);

    private void refreshToken() throws IOException {

        // Todo Use refresh token

        record Response(Data data, String desc, int status) {
            record Data(Token token) {
                record Token(String access_token, Instant token_expires_in) {
                }
            }
        }
        var response = client.post(Response.class, LOGIN_URI,
                new Field("countryCode", countryCode),
                new Field("app_id", APP_ID),
                new Field("grant_type", "password"),
                new Field("account", account),
                new Field("password", password));

        if (response.status != 0) {
            throw new IOException(String.format("Can't authenticate: [%d] %s", response.status, response.desc));
        }

        var token = response.data.token.access_token;

        Instant expiresAt;
        try {
            var now = Instant.now();
            expiresAt = response.data.token.token_expires_in;
            if (expiresAt == null) {
                expiresAt = now.plus(EXPIRES_AT_FALLBACK);
            }

            expiresAt = expiresAt.minus(expirationWindow);

            if (expiresAt.isBefore(now)) {
                expiresAt = now.plus(expirationWindow);
            }

        } catch (Exception e) {
            throw new IOException("Couldn't decode token: " + response.desc, e);
        }

        this.token = new Token(token, expiresAt);
    }
}
