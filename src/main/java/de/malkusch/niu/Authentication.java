package de.malkusch.niu;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import com.auth0.jwt.JWT;

final class Authentication {

    private final String account;
    private final String password;
    private final String countryCode;
    private final Client client;

    public Authentication(String account, String password, String countryCode, Duration expirationWindow, Client client)
            throws IOException {

        this.account = assertNotEmpty(account, "account must not be empty");
        this.password = assertNotEmpty(password, "password must not be empty");
        this.countryCode = assertNotEmpty(countryCode, "countryCode must not be empty");
        this.expirationWindow = requireNonNull(expirationWindow);
        this.client = requireNonNull(client);

        refreshToken();
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
    private static final String LOGIN_URI = "https://account-fk.niu.com/appv2/login";
    private static Duration EXPIRES_AT_FALLBACK = Duration.ofHours(1);

    private void refreshToken() throws IOException {

        record Response(Data data, String desc, int status) {
            record Data(String token) {
            }
        }
        var response = client.post(Response.class, LOGIN_URI, new Field("countryCode", countryCode),
                new Field("account", account), new Field("password", password));

        if (response.status != 0) {
            throw new IOException(String.format("Can't authenticate: [%d] %s", response.status, response.desc));
        }

        var token = response.data.token;

        Instant expiresAt;
        try {
            var jwt = JWT.decode(token);
            var now = Instant.now();
            expiresAt = jwt.getExpiresAtAsInstant();
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
