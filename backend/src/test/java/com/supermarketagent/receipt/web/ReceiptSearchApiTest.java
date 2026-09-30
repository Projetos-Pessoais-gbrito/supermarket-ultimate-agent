package com.supermarketagent.receipt.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ReceiptSearchApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        InsightTestData data = new InsightTestData(jdbc);
        data.reset();
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"correct-horse\"}"))
                .andReturn().getResponse().getContentAsString();
        bearer = "Bearer " + JsonPath.read(body, "$.accessToken");
        long ana = jdbc.queryForObject("SELECT id FROM users WHERE email = 'ana@example.com'", Long.class);
        long assai = data.store("SENDAS DISTRIBUIDORA S/A", "Assaí");
        long coffee = data.storeProduct(assai, data.product("CAFE", "BEBIDAS"), "CAFE PILAO 500G");
        data.receipt(ana, assai, "2026-09-05T15:00:00Z", coffee, "2", "10.00");
    }

    @Test
    void filtersAndReportsMonthlyTotals() throws Exception {
        mvc.perform(get("/api/receipts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .param("store", "Assaí").param("month", "2026-09").param("q", "pilao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].storeName").value("Assaí"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.monthlyTotals[0].month").value("2026-09"))
                .andExpect(jsonPath("$.monthlyTotals[0].total").value(20.00))
                .andExpect(jsonPath("$.stores[0]").value("Assaí"))
                .andExpect(jsonPath("$.months[0]").value("2026-09"));
    }

    @Test
    void rejectsAMalformedMonth() throws Exception {
        mvc.perform(get("/api/receipts").header(HttpHeaders.AUTHORIZATION, bearer).param("month", "09/2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnOverlongSearch() throws Exception {
        mvc.perform(get("/api/receipts").header(HttpHeaders.AUTHORIZATION, bearer).param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest());
    }
}
