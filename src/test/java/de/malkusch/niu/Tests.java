package de.malkusch.niu;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static de.malkusch.niu.Retry.Configuration.DISABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Tests {

    private static final String ACCOUNT = "any_account";
    private static final String PASSWORD = "any_pwd";
    private static final String COUNTRY_CODE = "49";
    private static final Duration EXPIRATION_WINDOW = Duration.ofSeconds(10);

    public static Authentication authentication(String access_token, Instant token_expires_in) {
        try {
            var httpClient = mock(HttpClient.class);
            when(httpClient.connectTimeout()).thenReturn(Optional.of(Duration.ofMillis(10)));
            var client = new Client(httpClient, Retry.build(DISABLED), "Any");

            var login = Files.readString(Paths.get(AuthenticationTest.class.getResource("login.json").toURI()))
                    .replace("{{access_token}}", access_token)
                    .replace("{{token_expires_in}}", Long.toString(token_expires_in.toEpochMilli()));

            var response = mock(HttpResponse.class);
            when(response.body()).thenReturn(login);
            when(response.statusCode()).thenReturn(200);

            when(httpClient.send(any(HttpRequest.class), any(BodyHandler.class))).thenReturn(response);

            return new Authentication(ACCOUNT, PASSWORD, COUNTRY_CODE, EXPIRATION_WINDOW, client);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
