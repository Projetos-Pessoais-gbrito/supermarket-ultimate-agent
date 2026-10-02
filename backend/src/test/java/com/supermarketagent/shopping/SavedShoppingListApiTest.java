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
class SavedShoppingListApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenService tokens;

    private long rice;
    private String ana;
    private String bia;

    @BeforeEach
    void setUp() {
        ShoppingTestData data = new ShoppingTestData(jdbc);
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
    void savesTheListAndReusesItNextMonth() throws Exception {
        long riceItem = addItem("{\"productId\":" + rice + ",\"quantity\":2}");
        addItem("{\"name\":\"Pão\"}");
        mvc.perform(as(ana, patch("/api/shopping-list/" + riceItem)).content("{\"checked\":true}"));

        long saved = save(" Compra do mês ");
        mvc.perform(as(ana, get("/api/shopping-list/saved")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Compra do mês"))
                .andExpect(jsonPath("$[0].itemCount").value(2));

        // Next month: the bought rice was cleared, the bread is still open
        mvc.perform(as(ana, delete("/api/shopping-list/checked"))).andExpect(status().isNoContent());
        mvc.perform(as(ana, post("/api/shopping-list/saved/" + saved + "/apply")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.alreadyInList").value(1));

        mvc.perform(as(ana, get("/api/shopping-list")))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Pão"))
                .andExpect(jsonPath("$[1].productId").value(rice))
                .andExpect(jsonPath("$[1].quantity").value(2))
                .andExpect(jsonPath("$[1].checked").value(false));
    }

    @Test
    void savingUnderTheSameNameReplacesTheList() throws Exception {
        addItem("{\"name\":\"Pão\"}");
        long first = save("Mensal");
        addItem("{\"name\":\"Café\"}");
        long second = save("MENSAL");

        assertThat(second).isEqualTo(first);
        mvc.perform(as(ana, get("/api/shopping-list/saved")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("MENSAL"))
                .andExpect(jsonPath("$[0].itemCount").value(2));
    }

    @Test
    void anEmptyListCannotBeSaved() throws Exception {
        mvc.perform(as(ana, post("/api/shopping-list/saved")).content("{\"name\":\"Mensal\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(ana, post("/api/shopping-list/saved")).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(ana, post("/api/shopping-list/saved")).content("{\"name\":\"" + "a".repeat(81) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savedListsOfOtherUsersAreInvisible() throws Exception {
        addItem("{\"name\":\"Pão\"}");
        long saved = save("Mensal");

        mvc.perform(as(bia, get("/api/shopping-list/saved"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(bia, post("/api/shopping-list/saved/" + saved + "/apply"))).andExpect(status().isNotFound());
        mvc.perform(as(bia, delete("/api/shopping-list/saved/" + saved))).andExpect(status().isNotFound());

        mvc.perform(as(ana, delete("/api/shopping-list/saved/" + saved))).andExpect(status().isNoContent());
        mvc.perform(as(ana, get("/api/shopping-list/saved"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void savedListsAreExportedAndDeletedWithTheAccount() throws Exception {
        addItem("{\"productId\":" + rice + ",\"quantity\":2}");
        save("Mensal");

        mvc.perform(as(ana, get("/api/me/export")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedShoppingLists.length()").value(1))
                .andExpect(jsonPath("$.savedShoppingLists[0].name").value("Mensal"))
                .andExpect(jsonPath("$.savedShoppingLists[0].items[0].name").value("ARROZ TIO JOAO 5KG"))
                .andExpect(jsonPath("$.savedShoppingLists[0].items[0].quantity").value(2));

        jdbc.update("DELETE FROM users WHERE email = 'ana@example.com'");
        assertThat(
                jdbc.queryForObject("SELECT count(*) FROM saved_shopping_list_items", Integer.class)).isZero();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/shopping-list/saved")).andExpect(status().isUnauthorized());
    }

    private long addItem(String body) throws Exception {
        String response = mvc.perform(as(ana, post("/api/shopping-list")).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long save(String name) throws Exception {
        String response = mvc.perform(as(ana, post("/api/shopping-list/saved")).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
