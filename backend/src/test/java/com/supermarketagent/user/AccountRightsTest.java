package com.supermarketagent.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.receipt.provider.NfceProviderRegistry;
import com.supermarketagent.receipt.support.FixtureSpProvider;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountRightsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private NfceProviderRegistry registry;

    private String accessToken;
    private String refreshToken;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE receipt_payments, receipt_items, receipts, store_products, products, stores, "
                + "refresh_tokens, users RESTART IDENTITY CASCADE");
        when(registry.providerFor(any())).thenReturn(new FixtureSpProvider());
        MvcResult registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isCreated()).andReturn();
        accessToken = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
        refreshToken = JsonPath.read(registered.getResponse().getContentAsString(), "$.refreshToken");
        mvc.perform(post("/api/receipts").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrCodeUrl\":\"" + FixtureSpProvider.QR_URL + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void exportsAccountAndReceiptsAsADownload() throws Exception {
        mvc.perform(get("/api/me/export").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.containsString("meus-dados.json")))
                .andExpect(jsonPath("$.account.email").value("ana@example.com"))
                .andExpect(jsonPath("$.account.createdAt").exists())
                .andExpect(jsonPath("$.receipts.length()").value(1))
                .andExpect(jsonPath("$.receipts[0].items.length()").value(10))
                .andExpect(jsonPath("$.receipts[0].payments[0].method").value("Cartão de Crédito"))
                .andExpect(jsonPath("$..passwordHash").isEmpty());
    }

    @Test
    void deletesTheAccountAndAllPersonalData() throws Exception {
        mvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"correct-horse\"}"))
                .andExpect(status().isNoContent());

        assertThat(count("users")).isZero();
        assertThat(count("receipts")).isZero();
        assertThat(count("receipt_items")).isZero();
        assertThat(count("receipt_payments")).isZero();
        assertThat(count("refresh_tokens")).isZero();
        assertThat(count("stores")).as("stores are shared reference data").isEqualTo(1);
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keepsTheAccountWhenThePasswordIsWrong() throws Exception {
        mvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isForbidden());

        assertThat(count("users")).isEqualTo(1);
        assertThat(count("receipts")).isEqualTo(1);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/me/export")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/me").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }
}
