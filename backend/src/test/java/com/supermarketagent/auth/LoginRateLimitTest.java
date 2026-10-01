package com.supermarketagent.auth;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supermarketagent.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end brute-force protection with low limits (own application context). */
@SpringBootTest(properties = {
    "app.security.login-attempts.max-failures-per-email=3",
    "app.security.login-attempts.max-failures-per-ip=6",
    "app.security.login-attempts.max-registrations-per-ip=4",
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginRateLimitTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE refresh_tokens, users RESTART IDENTITY CASCADE");
        limiter.clear();
        register("ana@example.com").andExpect(status().isCreated());
    }

    @Test
    void answers429WithRetryAfterAfterTooManyWrongPasswords() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("ana@example.com", "wrong-password").andExpect(status().isUnauthorized());
        }

        login("ana@example.com", "wrong-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")))
                .andExpect(jsonPath("$.status").value(429));
        // Even the right password waits until the window passes
        login("ana@example.com", "correct-horse").andExpect(status().isTooManyRequests());
    }

    @Test
    void unknownEmailsAreLimitedTheSameWay() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("nobody@example.com", "wrong-password").andExpect(status().isUnauthorized());
        }

        login("nobody@example.com", "wrong-password").andExpect(status().isTooManyRequests());
    }

    @Test
    void otherEmailsKeepWorking() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("nobody@example.com", "wrong-password");
        }

        login("ana@example.com", "correct-horse").andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsTheCounter() throws Exception {
        login("ana@example.com", "wrong-password");
        login("ana@example.com", "wrong-password");
        login("ana@example.com", "correct-horse").andExpect(status().isOk());

        login("ana@example.com", "wrong-password").andExpect(status().isUnauthorized());
        login("ana@example.com", "wrong-password").andExpect(status().isUnauthorized());
        login("ana@example.com", "correct-horse").andExpect(status().isOk());
    }

    @Test
    void limitsRegistrationsFromOneIp() throws Exception {
        // setUp already registered once
        register("b@example.com").andExpect(status().isCreated());
        register("c@example.com").andExpect(status().isCreated());
        register("d@example.com").andExpect(status().isCreated());

        register("e@example.com").andExpect(status().isTooManyRequests());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"));
    }
}
