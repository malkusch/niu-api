package de.malkusch.niu;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static java.net.URLEncoder.encode;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.malkusch.niu.Authentication.Token;

record Field(String name, String value) {

    String urlencoded() {
        try {
            return encode(name, "UTF-8") + "=" + encode(value, "UTF-8");

        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}

final class Client {

    private static final String DEFAULT_USER_AGENT = "manager/4.6.2 (android; Unknown);brand=Unknown;model=Unknown;clientIdentifier=Overseas;lang=en-US";

    private final String userAgent;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public Client(Duration timeout) {
        this(timeout, DEFAULT_USER_AGENT);
    }

    public Client(Duration timeout, String userAgent) {
        this.timeout = requireNonNull(timeout);
        if (!timeout.isPositive()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        this.userAgent = requireNonNull(userAgent);
        if (userAgent.isEmpty()) {
            throw new IllegalArgumentException("userAgent must not be empty");
        }

        httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();

        mapper = new ObjectMapper();
        mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public <T> T post(Class<T> type, String url, Field... fields) throws IOException {
        return post(type, url, null, fields);
    }

    public <T> T post(Class<T> type, String url, Token token, Field... fields) throws IOException {
        var body = stream(fields).map(Field::urlencoded).reduce("", (f1, f2) -> f1 + "&" + f2);
        var requestBuilder = request(url).POST(BodyPublishers.ofString(body)).setHeader("Content-Type",
                "application/x-www-form-urlencoded");
        return send(type, token, requestBuilder);
    }

    public <T> T get(Class<T> type, String url, Token token) throws IOException {
        return send(type, token, request(url).GET());
    }

    private <T> T send(Class<T> type, Token token, HttpRequest.Builder requestBuilder) throws IOException {
        if (token != null) {
            requestBuilder.setHeader("token", token.value());
        }
        var request = requestBuilder.build();
        try {
            var response = httpClient.send(request, BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                return mapper.readValue(stream, type);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).setHeader("User-Agent", userAgent).timeout(timeout);
    }
}
