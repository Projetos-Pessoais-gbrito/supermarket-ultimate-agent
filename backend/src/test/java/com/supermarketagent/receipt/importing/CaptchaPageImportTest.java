package com.supermarketagent.receipt.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.receipt.provider.NfcePageParseException;
import com.supermarketagent.receipt.provider.NfceProviderRegistry;
import com.supermarketagent.receipt.provider.sp.SpNfceProvider;
import com.supermarketagent.receipt.provider.sp.SpSefazProperties;
import com.supermarketagent.receipt.support.FixtureSpProvider;
import java.time.Duration;
import java.util.Map;
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
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Importing the SEFAZ page the user opened in the app after solving the captcha. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CaptchaPageImportTest {

    private static final String KEY = FixtureSpProvider.KEY;

    @Autowired
    private ReceiptImportService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NfceProviderRegistry registry;

    private long userId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE receipt_payments, receipt_items, receipts, store_products, products, stores, "
                + "refresh_tokens, users RESTART IDENTITY CASCADE");
        // Real SP provider: parsing a user page never touches the network
        SpSefazProperties properties = new SpSefazProperties(Duration.ofSeconds(1), Duration.ofSeconds(1),
                Duration.ZERO, 1, Duration.ZERO, "test");
        when(registry.providerFor(any())).thenReturn(new SpNfceProvider(RestClient.create(), properties));
        userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash) VALUES ('ana@example.com', 'hash') RETURNING id", Long.class);
    }

    @Test
    void importsThePageAndMarksItsSource() {
        ReceiptImportResult result = service.importFromCaptchaPage(userId, KEY, FixtureSpProvider.fixture());

        assertThat(result.created()).isTrue();
        Map<String, Object> receipt = jdbc.queryForMap("SELECT source, source_url, total_amount FROM receipts");
        assertThat(receipt.get("source")).isEqualTo("CAPTCHA_PAGE");
        assertThat((String) receipt.get("source_url")).endsWith("ConsultaPublica.aspx?chNFe=" + KEY);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receipt_items", Integer.class)).isEqualTo(10);
    }

    @Test
    void acceptsTheKeyAsPrintedWithSpaces() {
        String printed = KEY.replaceAll("(.{4})", "$1 ").strip();

        assertThat(service.importFromCaptchaPage(userId, printed, FixtureSpProvider.fixture()).created()).isTrue();
    }

    @Test
    void rejectsAPageOfAnotherReceipt() {
        String otherKey = "35260111222333000181650010000123451123456060";

        assertThatThrownBy(() -> service.importFromCaptchaPage(userId, otherKey, FixtureSpProvider.fixture()))
                .isInstanceOf(NfcePageParseException.class)
                .hasMessageContaining("different receipt");
    }

    @Test
    void rejectsAPageWhoseStoreIsNotTheIssuerOfTheKey() {
        String forged = FixtureSpProvider.fixture().replace("11.222.333/0001-81", "06.057.223/0264-80");

        assertThatThrownBy(() -> service.importFromCaptchaPage(userId, KEY, forged))
                .isInstanceOf(NfcePageParseException.class)
                .hasMessageContaining("issuer");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receipts", Integer.class)).isZero();
    }

    @Test
    void neverRenamesStoresOrProductsOtherUsersSee() {
        jdbc.update("INSERT INTO stores (cnpj, name, display_name, state_code) "
                + "VALUES ('11222333000181', 'NOME VERIFICADO LTDA', 'NOME VERIFICADO', '35')");
        long storeId = jdbc.queryForObject("SELECT id FROM stores", Long.class);
        jdbc.update("INSERT INTO store_products (store_id, store_code, description, unit) "
                + "VALUES (?, '94794', 'DESCRICAO VERIFICADA', 'KG')", storeId);

        service.importFromCaptchaPage(userId, KEY, FixtureSpProvider.fixture());

        assertThat(jdbc.queryForObject("SELECT name FROM stores", String.class)).isEqualTo("NOME VERIFICADO LTDA");
        assertThat(jdbc.queryForObject("SELECT description FROM store_products WHERE store_code = '94794'",
                String.class)).isEqualTo("DESCRICAO VERIFICADA");
    }

    @Test
    void rejectsTheCaptchaPageItself() {
        String captchaPage = "<html><body>Digite os caracteres da imagem ao lado</body></html>";

        assertThatThrownBy(() -> service.importFromCaptchaPage(userId, KEY, captchaPage))
                .isInstanceOf(NfcePageParseException.class);
    }

    @Test
    void removesBuyerDataFromThePage() {
        String withBuyer = FixtureSpProvider.fixture().replace("Consumidor não identificado", "CPF: 123.456.789-09");

        service.importFromCaptchaPage(userId, KEY, withBuyer);

        assertThat(jdbc.queryForObject("SELECT raw_html FROM receipts", String.class)).doesNotContain("123.456.789-09");
    }

    @Test
    void endpointImportsThePage() throws Exception {
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bia@example.com\",\"password\":\"correct-horse\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(registered, "$.accessToken");
        String body = JsonMapper.builder().build()
                .writeValueAsString(Map.of("accessKey", KEY, "html", FixtureSpProvider.fixture()));

        mvc.perform(post("/api/receipts/page").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(10));
    }
}
