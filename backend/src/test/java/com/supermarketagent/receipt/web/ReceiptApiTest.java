package com.supermarketagent.receipt.web;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.receipt.provider.NfceProviderRegistry;
import com.supermarketagent.receipt.provider.SefazUnavailableException;
import com.supermarketagent.receipt.support.FixtureSpProvider;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReceiptApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private NfceProviderRegistry registry;

    private final FixtureSpProvider provider = new FixtureSpProvider();
    private String ana;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE receipt_payments, receipt_items, receipts, store_products, products, stores, users "
                + "RESTART IDENTITY CASCADE");
        when(registry.providerFor(any())).thenReturn(provider);
        ana = register("ana@example.com");
    }

    @Test
    void importsReceiptAndReturnsItsDetails() throws Exception {
        importReceipt(ana, FixtureSpProvider.QR_URL)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/receipts/1")))
                .andExpect(jsonPath("$.store.name").value("SUPERMERCADO EXEMPLO"))
                .andExpect(jsonPath("$.store.legalName").value("SUPERMERCADO EXEMPLO LTDA"))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].description").value("PAO FRANCES CONG KG BALCAO"))
                .andExpect(jsonPath("$.items[0].quantity").value(0.136))
                .andExpect(jsonPath("$.totalAmount").value(52.92))
                .andExpect(jsonPath("$.issuedAt").value("2026-01-15T13:30:00Z"))
                .andExpect(jsonPath("$.payments[0].method").value("Cartão de Crédito"));
    }

    @Test
    void importingAgainReturnsTheExistingReceipt() throws Exception {
        importReceipt(ana, FixtureSpProvider.QR_URL).andExpect(status().isCreated());

        importReceipt(ana, FixtureSpProvider.QR_URL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void listsOwnReceiptsNewestFirst() throws Exception {
        importReceipt(ana, FixtureSpProvider.QR_URL);

        mvc.perform(get("/api/receipts").header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].storeName").value("SUPERMERCADO EXEMPLO"))
                .andExpect(jsonPath("$.content[0].itemCount").value(10))
                .andExpect(jsonPath("$.content[0].totalAmount").value(52.92));
    }

    @Test
    void usersCannotSeeEachOthersReceipts() throws Exception {
        importReceipt(ana, FixtureSpProvider.QR_URL);
        String bia = register("bia@example.com");

        mvc.perform(get("/api/receipts/1").header(HttpHeaders.AUTHORIZATION, bearer(bia)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/receipts").header(HttpHeaders.AUTHORIZATION, bearer(bia)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/receipts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/receipts").contentType(MediaType.APPLICATION_JSON).content("{\"qrCodeUrl\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsQrCodesThatAreNotReceipts() throws Exception {
        importReceipt(ana, "https://example.com/not-a-receipt")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void explainsThatKeyOnlyLinksNeedTheQrCode() throws Exception {
        importReceipt(ana, "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaPublica.aspx?chNFe="
                + FixtureSpProvider.KEY)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("KEY_ONLY_LINK"));
    }

    @Test
    void reportsSefazOutageAsServiceUnavailable() throws Exception {
        provider.failWith(new SefazUnavailableException("down", null));

        importReceipt(ana, FixtureSpProvider.QR_URL).andExpect(status().isServiceUnavailable());
    }

    @Test
    void limitsPageSize() throws Exception {
        mvc.perform(get("/api/receipts?size=500").header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                .andExpect(status().isBadRequest());
    }

    private ResultActions importReceipt(String token, String qrCodeUrl) throws Exception {
        return mvc.perform(post("/api/receipts")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrCodeUrl\":\"" + qrCodeUrl + "\"}"));
    }

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
