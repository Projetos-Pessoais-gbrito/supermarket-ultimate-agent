package com.supermarketagent.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.insight.InsightTestData;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BudgetApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        new InsightTestData(jdbc).reset();
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"correct-horse\"}"))
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(body, "$.accessToken");
    }

    @Test
    void savesTheBudgetAndReportsItsStatus() throws Exception {
        mvc.perform(put("/api/budget").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overall\":800.00,\"categories\":[{\"category\":\"PADARIA\",\"limit\":60.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").value(800.00))
                .andExpect(jsonPath("$.categories[0].label").value("Padaria"));

        mvc.perform(get("/api/insights/budget").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall.limit").value(800.00))
                .andExpect(jsonPath("$.overall.state").value("OK"))
                .andExpect(jsonPath("$.categories[0].category").value("PADARIA"));
    }

    @Test
    void listsTheCategoriesALimitCanBeSetFor() throws Exception {
        mvc.perform(get("/api/budget/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("HORTIFRUTI"))
                .andExpect(jsonPath("$[0].label").value("Hortifruti"));
    }

    @Test
    void rejectsInvalidBudgetsWithProblemDetails() throws Exception {
        mvc.perform(put("/api/budget").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overall\":-5,\"categories\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/budget")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/insights/budget")).andExpect(status().isUnauthorized());
    }
}
