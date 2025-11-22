package de.malkusch.niu;

import de.malkusch.niu.Authentication.Token;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

record Field(String name, String value) {

    String urlencoded() {
        return encode(name, UTF_8) + "=" + encode(value, UTF_8);
    }
}

final class Client {

    private static final String DEFAULT_USER_AGENT = "manager/4.6.2 (android; Unknown);brand=Unknown;model=Unknown;clientIdentifier=Overseas;lang=en-US";

    private final String userAgent;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Retry<String> retry;

    public Client(Duration timeout, Retry<String> retry) {
        this(timeout, retry, DEFAULT_USER_AGENT);
    }

    public Client(Duration timeout, Retry<String> retry, String userAgent) {
        this(HttpClient.newBuilder().connectTimeout(timeout).build(), retry, userAgent);
    }

    Client(HttpClient httpClient, Retry<String> retry, String userAgent) {
        this.httpClient = requireNonNull(httpClient);
        this.timeout = requireNonNull(httpClient.connectTimeout().get());
        this.retry = retry;

        this.userAgent = requireNonNull(userAgent);
        if (userAgent.isEmpty()) {
            throw new IllegalArgumentException("userAgent must not be empty");
        }

        mapper = JsonMapper.builder().configure(FAIL_ON_UNKNOWN_PROPERTIES, false).build();
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
        String response = null;
        try {
            try {
                response = retry.<IOException, InterruptedException>retry(() -> _send_unsafe(request));
                return mapper.readValue(response, type);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);

            }
        } catch (JacksonException e) {
            throw new IOException("Failed parsing JSON:\n" + response, e);
        }
    }

    private String _send_unsafe(HttpRequest request) throws IOException, InterruptedException {
        var response = httpClient.send(request, BodyHandlers.ofString());

        if (response.statusCode() < 100) {
            throw new IOException("Query " + request + " failed with response code " + response.statusCode());

        } else if (response.statusCode() < 400) {
            return response.body();

        } else {
            throw new IOException("Query " + request + " failed with response code " + response.statusCode());
        }
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).setHeader("User-Agent", userAgent).timeout(timeout);
    }
}
