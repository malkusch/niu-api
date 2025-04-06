package de.malkusch.niu;

import static de.malkusch.niu.Tests.authentication;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.time.Instant;

public class AuthenticationTest {

    @Test
    public void validTokenshouldLogin() throws Exception {
        var valid = "eyJhbGciOiJIUzUxMiIsImtpZCI6IjlIOHZmUmVDUmJDU3ZqMTdGa0pLMXFtSXFnZjJLNXlsMjBRdyIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJhbnkiLCJleHAiOjQ4MjI3MDI1MDgsInN1YiI6ImFueSJ9.V2iwvKc2YJL3VV4BOycFRVeeTqtCXIlgNx39OSK9JE_Ao60E3sKHWItSFH4An30-5bjDG_AwBc_Sp4iiB8mj3g";
        var authentication = authentication(valid, Instant.parse("2052-01-01T00:00:00Z"));
        assertEquals(valid, authentication.token().value());
    }
    
    @Test
    public void expiredTokenShouldLogin() throws Exception {
        var expired = "eyJhbGciOiJIUzUxMiIsImtpZCI6IjlIOHZmUmVDUmJDU3ZqMTdGa0pLMXFtSXFnZjJLNXlsMjBRdyIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJhbnkiLCJleHAiOjE2MzU0OTI5MDgsInN1YiI6ImFueSJ9.ftGQ_cu9xLNIlmSzPjBIcv7-9GgmPPvyGPNYftC7GT9hUv7tCjwxGgzJf6iy7x16M5BC0eFGI4L5WhNya416og";
        var authentication = authentication(expired, Instant.parse("2022-01-01T00:00:00Z"));
        assertEquals(expired, authentication.token().value());
    }
}
