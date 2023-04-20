package de.malkusch.niu;

import static de.malkusch.niu.Retry.Configuration.DISABLED;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.OngoingStubbing;

import de.malkusch.niu.Retry.Configuration;

public class ClientTest {

    private HttpClient httpClient = mock(HttpClient.class);
    private final static String ANY_URL = "http://example.org/";

    @BeforeEach
    void setupHttpClient() {
        when(httpClient.connectTimeout()).thenReturn(Optional.of(Duration.ofMillis(10)));
    }

    private Client client() {
        return client(TEST_RETRY);
    }

    private Client client(Retry.Configuration retry) {
        return new Client(httpClient, Retry.build(retry), "Any");
    }

    public static final Retry.Configuration TEST_RETRY = new Configuration(3, Duration.ofMillis(100));

    public static final Retry.Configuration[] ALL_RETRIES() {
        return new Retry.Configuration[] { DISABLED, TEST_RETRY };
    }

    @Test
    void disabledShoudNotRetry() throws Exception {
        var client = client(DISABLED);
        givenException(IOException.class);

        assertThrows(IOException.class, () -> {
            client.post(String.class, ANY_URL);
        });

        verify(httpClient, atMostOnce()).send(any(), any());
    }

    @ParameterizedTest
    @MethodSource("ALL_RETRIES")
    void shouldThrowInterrupted(Retry.Configuration retry) throws Exception {
        var client = client(retry);
        givenException(InterruptedException.class);

        assertThrows(IOException.class, () -> {
            client.post(String.class, ANY_URL);
        });
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("ALL_RETRIES")
    void shouldThrowIOException(Retry.Configuration retry) throws Exception {
        var client = client(retry);
        givenException(IOException.class);

        assertThrows(IOException.class, () -> {
            client.post(String.class, ANY_URL);
        });
    }

    @ParameterizedTest
    @MethodSource("ALL_RETRIES")
    void shouldThrowHttpTimeoutException(Retry.Configuration retry) throws Exception {
        var client = client(retry);
        givenException(HttpTimeoutException.class);

        assertThrows(HttpTimeoutException.class, () -> {
            client.post(String.class, ANY_URL);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 99, 400, 499, 500, 599 })
    void shouldThrowIOExceptionOnHttpError(int error) throws Exception {
        var client = client();
        givenResponse(response("\"Test\"", error));

        assertThrows(IOException.class, () -> {
            client.post(String.class, ANY_URL);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = { 200, 201, 299, 300, 301, 399 })
    void shouldAcceptSuccessHttpCodes(int status) throws Exception {
        var client = client();
        givenResponse(response("\"Test\"", status));

        var response = client.post(String.class, ANY_URL);

        assertEquals("Test", response);
    }

    @ParameterizedTest
    @MethodSource("ALL_RETRIES")
    void shouldThrowRuntimeException(Retry.Configuration retry) throws Exception {
        class AnyRuntimException extends RuntimeException {
        }

        var client = client(retry);
        givenException(AnyRuntimException.class);

        assertThrows(AnyRuntimException.class, () -> {
            client.post(String.class, ANY_URL);
        });
    }

    @ParameterizedTest
    @MethodSource("ALL_RETRIES")
    void shouldNotRetryWhenSuccess(Retry.Configuration retry) throws Exception {
        var client = client(retry);
        givenResponse("\"Test\"");

        var response = client.post(String.class, ANY_URL);

        assertEquals("Test", response);
        verify(httpClient, atMostOnce()).send(any(), any());
    }

    @Test
    void shouldRetryWhenException() throws Exception {
        var client = client();
        givenException(IOException.class) //
                .thenReturn(response("\"Test\""));

        var response = client.post(String.class, ANY_URL);
        assertEquals("Test", response);
        verify(httpClient, times(2)).send(any(), any());

    }

    @Test
    void shouldRetryWhenHttpError() throws Exception {
        var client = client();
        givenResponse(response("\"Test\"", 500)) //
                .thenReturn(response("\"Test\""));

        var response = client.post(String.class, ANY_URL);
        assertEquals("Test", response);
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    public void shouldWaitWhenRetrying() throws Exception {
        var client = client(new Configuration(3, Duration.ofMillis(500)));
        givenResponse(response("\"Test\"", 500)) //
                .thenReturn(response("\"Test\"", 500)) //
                .thenReturn(response("\"Test\""));

        var stopwatch = StopWatch.createStarted();
        var response = client.post(String.class, ANY_URL);
        stopwatch.stop();

        assertEquals("Test", response);
        var seconds = stopwatch.getTime(MILLISECONDS) / 1000.0;
        assertTrue(seconds > 1, "The retry was too fast: " + seconds + " seconds");
        verify(httpClient, times(3)).send(any(), any());
    }

    private OngoingStubbing<HttpResponse> givenException(Class<? extends Throwable> exception) throws Exception {
        return when(httpClient.send(any(HttpRequest.class), any(BodyHandler.class))).thenThrow(exception);
    }

    private OngoingStubbing<HttpResponse> givenResponse(HttpResponse<?> response) throws Exception {
        return when(httpClient.send(any(HttpRequest.class), any(BodyHandler.class))).thenReturn(response);
    }

    private OngoingStubbing<HttpResponse> givenResponse(String response) throws Exception {
        return givenResponse(response(response));
    }

    private static HttpResponse<String> response(String response) {
        return response(response, 200);
    }

    private static HttpResponse<String> response(String response, int status) {
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.body()).thenReturn(response);
        when(httpResponse.statusCode()).thenReturn(status);
        return httpResponse;
    }
}
