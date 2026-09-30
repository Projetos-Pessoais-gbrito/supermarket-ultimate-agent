package com.supermarketagent.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUsers() {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
    }

    @Test
    void registeredUserCanCallProtectedEndpoints() throws Exception {
        String token = accessToken(register(" Ana@Example.com ", "correct-horse").andExpect(status().isCreated()));

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"));
    }

    @Test
    void storesOnlyAPasswordHash() throws Exception {
        register("ana@example.com", "correct-horse");

        String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("correct-horse");
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        register("ana@example.com", "correct-horse");

        register("ANA@example.com", "another-password").andExpect(status().isConflict());
    }

    @Test
    void loginReturnsTokenForValidCredentials() throws Exception {
        register("ana@example.com", "correct-horse");

        login("ana@example.com", "correct-horse")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginFailsWithTheSameErrorForWrongPasswordAndUnknownEmail() throws Exception {
        register("ana@example.com", "correct-horse");

        login("ana@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid e-mail or password"));
        login("nobody@example.com", "correct-horse")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid e-mail or password"));
    }

    @Test
    void validatesEmailAndPassword() throws Exception {
        register("not-an-email", "correct-horse").andExpect(status().isBadRequest());
        register("ana@example.com", "short").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPasswordsLongerThanBcryptAccepts() throws Exception {
        // 40 accented characters = 80 bytes in UTF-8, above BCrypt's 72-byte limit
        register("ana@example.com", "á".repeat(40)).andExpect(status().isBadRequest());
        login("ana@example.com", "á".repeat(40)).andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshReturnsANewPairAndRetiresTheOldRefreshToken() throws Exception {
        String first = refreshToken(register("ana@example.com", "correct-horse"));

        ResultActions refreshed = refresh(first).andExpect(status().isOk());
        String second = refreshToken(refreshed);

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(refreshed)))
                .andExpect(status().isOk());
        refresh(first).andExpect(status().isUnauthorized());
    }

    @Test
    void reusingARotatedRefreshTokenEndsEverySession() throws Exception {
        String stolen = refreshToken(register("ana@example.com", "correct-horse"));
        String legit = refreshToken(refresh(stolen));

        refresh(stolen).andExpect(status().isUnauthorized());

        refresh(legit).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String token = refreshToken(register("ana@example.com", "correct-horse"));

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownAndExpiredRefreshTokens() throws Exception {
        refresh("not-a-real-token").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Session expired, please log in again"));

        String token = refreshToken(register("ana@example.com", "correct-horse"));
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute'");
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void storesOnlyAHashOfRefreshTokens() throws Exception {
        String token = refreshToken(register("ana@example.com", "correct-horse"));

        assertThat(jdbc.queryForObject("SELECT token_hash FROM refresh_tokens", String.class))
                .hasSize(64).isNotEqualTo(token);
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private static String refreshToken(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.refreshToken");
    }

    private static String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String accessToken(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }
}
