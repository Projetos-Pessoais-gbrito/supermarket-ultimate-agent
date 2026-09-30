package com.supermarketagent.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.auth.TokenService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShoppingListApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenService tokens;

    private ShoppingTestData data;
    private long rice;
    private String ana;
    private String bia;

    @BeforeEach
    void setUp() {
        data = new ShoppingTestData(jdbc);
        data.reset();
        long anaId = data.user("ana@example.com");
        long biaId = data.user("bia@example.com");
        long assai = data.store("ASSAI");
        rice = data.product("ARROZ TIO JOAO 5KG");
        data.buy(anaId, assai, "2026-08-01", data.storeProduct(assai, rice), "1", "25.00");
        ana = tokens.issueFor(anaId).accessToken();
        bia = tokens.issueFor(biaId).accessToken();
    }

    @Test
    void addsChecksAndRemovesItems() throws Exception {
        long id = add(ana, "{\"productId\":" + rice + ",\"quantity\":2}");
        add(ana, "{\"name\":\"  Pão de queijo \"}");

        mvc.perform(as(ana, patch("/api/shopping-list/" + id)).content("{\"checked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checked").value(true));
        mvc.perform(as(ana, get("/api/shopping-list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Pão de queijo"))
                .andExpect(jsonPath("$[0].productId").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("ARROZ TIO JOAO 5KG"))
                .andExpect(jsonPath("$[1].quantity").value(2))
                .andExpect(jsonPath("$[1].checked").value(true));

        mvc.perform(as(ana, delete("/api/shopping-list/checked"))).andExpect(status().isNoContent());
        mvc.perform(as(ana, get("/api/shopping-list"))).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void addingAProductAlreadyInTheListReturnsTheSameItem() throws Exception {
        long first = add(ana, "{\"productId\":" + rice + "}");
        long second = add(ana, "{\"productId\":" + rice + "}");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void uncheckingDoesNotDuplicateAnOpenProduct() throws Exception {
        long old = add(ana, "{\"productId\":" + rice + "}");
        mvc.perform(as(ana, patch("/api/shopping-list/" + old)).content("{\"checked\":true}"));
        long fresh = add(ana, "{\"productId\":" + rice + "}");

        mvc.perform(as(ana, patch("/api/shopping-list/" + old)).content("{\"checked\":false}"))
                .andExpect(status().isOk());

        mvc.perform(as(ana, get("/api/shopping-list")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(old));
        assertThat(fresh).isNotEqualTo(old);
    }

    @Test
    void onlyProductsTheUserBoughtCanBeAdded() throws Exception {
        mvc.perform(as(bia, post("/api/shopping-list")).content("{\"productId\":" + rice + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void itemsOfOtherUsersAreInvisible() throws Exception {
        long id = add(ana, "{\"name\":\"Café\"}");

        mvc.perform(as(bia, get("/api/shopping-list"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(bia, patch("/api/shopping-list/" + id)).content("{\"checked\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(bia, delete("/api/shopping-list/" + id))).andExpect(status().isNotFound());
        mvc.perform(as(bia, delete("/api/shopping-list/checked"))).andExpect(status().isNoContent());

        mvc.perform(as(ana, delete("/api/shopping-list/" + id))).andExpect(status().isNoContent());
    }

    @Test
    void rejectsInvalidItems() throws Exception {
        mvc.perform(as(ana, post("/api/shopping-list")).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(ana, post("/api/shopping-list")).content("{\"name\":\"" + "a".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(ana, post("/api/shopping-list")).content("{\"name\":\"Café\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(ana, patch("/api/shopping-list/1")).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void insightsAreServedToTheUser() throws Exception {
        mvc.perform(as(ana, get("/api/insights/shopping-list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mvc.perform(as(ana, get("/api/insights/price-alerts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/shopping-list")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/insights/shopping-list")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/insights/price-alerts")).andExpect(status().isUnauthorized());
    }

    private long add(String token, String body) throws Exception {
        String response = mvc.perform(as(token, post("/api/shopping-list")).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
