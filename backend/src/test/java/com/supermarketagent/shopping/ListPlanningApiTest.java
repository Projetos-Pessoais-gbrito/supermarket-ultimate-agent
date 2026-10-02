package com.supermarketagent.shopping;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.auth.TokenService;
import com.supermarketagent.shopping.ShoppingTestData.Line;
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
class ListPlanningApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenService tokens;

    private ShoppingTestData data;
    private long anaId;
    private long assaiCentro;
    private long assaiNorte;
    private long carrefour;
    private long rice;
    private long milk;
    private long banana;
    private String ana;
    private String bia;

    @BeforeEach
    void setUp() {
        data = new ShoppingTestData(jdbc);
        data.reset();
        anaId = data.user("ana@example.com");
        long biaId = data.user("bia@example.com");
        assaiCentro = data.store("SENDAS DISTRIBUIDORA S/A", "ASSAI");
        assaiNorte = data.store("SENDAS DISTRIBUIDORA S/A FILIAL", "ASSAI");
        carrefour = data.store("CARREFOUR COMERCIO", "CARREFOUR");
        rice = product("ARROZ TIO JOAO 5KG", "Arroz Tio João 5 kg");
        milk = product("LEITE INTEGRAL ITALAC 1L", "Leite integral Italac 1 L");
        banana = product("BANANA PRATA KG", "Banana prata");
        ana = tokens.issueFor(anaId).accessToken();
        bia = tokens.issueFor(biaId).accessToken();
    }

    @Test
    void suggestsOnlyProductsTheUserBoughtMatchingEveryWord() throws Exception {
        long biaId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'bia@example.com'", Long.class);
        data.buy(anaId, assaiCentro, "2026-08-01", data.storeProduct(assaiCentro, rice), "1", "25.00");
        data.buy(anaId, assaiCentro, "2026-08-02", data.storeProduct(assaiCentro, milk), "1", "4.99");
        data.buy(biaId, carrefour, "2026-08-03", data.storeProduct(carrefour, banana), "1", "6.99");

        mvc.perform(as(ana, get("/api/shopping-list/products").param("q", "arroz joao")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(rice))
                .andExpect(jsonPath("$[0].name").value("Arroz Tio João 5 kg"));
        mvc.perform(as(ana, get("/api/shopping-list/products").param("q", "LEITE")))
                .andExpect(jsonPath("$[0].productId").value(milk));
        mvc.perform(as(ana, get("/api/shopping-list/products").param("q", "banana")))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(ana, get("/api/shopping-list/products").param("q", "100%")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listsMarketsWithBranchesCombinedMostRecentFirst() throws Exception {
        data.buy(anaId, assaiCentro, "2026-07-01", data.storeProduct(assaiCentro, rice), "1", "25.00");
        data.buy(anaId, carrefour, "2026-08-01", data.storeProduct(carrefour, rice), "1", "27.00");
        data.buy(anaId, assaiNorte, "2026-09-01", data.storeProduct(assaiNorte, rice), "1", "24.00");

        mvc.perform(as(ana, get("/api/shopping-list/markets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("ASSAI"))
                .andExpect(jsonPath("$[1].name").value("CARREFOUR"));
        mvc.perform(as(bia, get("/api/shopping-list/markets"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void pricesListProductsFromTheLatestReceiptAtTheMarket() throws Exception {
        long riceCentro = data.storeProduct(assaiCentro, rice);
        long riceNorte = data.storeProduct(assaiNorte, rice);
        long bananaCentro = data.storeProduct(assaiCentro, banana);
        data.receipt(anaId, assaiCentro, "2026-07-01",
                new Line(riceCentro, "1", "25.00"), new Line(bananaCentro, "1.250", "5.98", "KG"));
        data.receipt(anaId, assaiNorte, "2026-09-01", new Line(riceNorte, "2", "23.50"));
        data.buy(anaId, carrefour, "2026-09-10", data.storeProduct(carrefour, milk), "1", "4.99");
        add("{\"productId\":" + rice + "}");
        add("{\"productId\":" + banana + "}");
        add("{\"productId\":" + milk + "}");
        add("{\"name\":\"Pão\"}");

        mvc.perform(as(ana, get("/api/shopping-list/prices").param("market", "ASSAI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value("ASSAI"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.productId == " + rice + ")].unitPrice").value(23.5))
                .andExpect(jsonPath("$.items[?(@.productId == " + banana + ")].unitPrice").value(5.98))
                .andExpect(jsonPath("$.items[?(@.productId == " + banana + ")].unit").value("KG"));
        mvc.perform(as(bia, get("/api/shopping-list/prices").param("market", "ASSAI")))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(as(ana, get("/api/shopping-list/prices").param("market", " ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addsChosenReceiptLinesWithTheirQuantities() throws Exception {
        long riceCentro = data.storeProduct(assaiCentro, rice);
        long unmatched = data.storeProduct(assaiCentro, null);
        long receiptId = data.receipt(anaId, assaiCentro, "2026-09-01",
                new Line(riceCentro, "1", "25.00"),
                new Line(data.storeProduct(assaiCentro, milk), "12", "4.99"),
                new Line(riceCentro, "1", "25.00"),
                new Line(unmatched, "1", "3.50"));
        add("{\"productId\":" + milk + "}");

        mvc.perform(as(ana, post("/api/shopping-list/from-receipt"))
                        .content("{\"receiptId\":" + receiptId + ",\"lineNumbers\":[1,2,3,4]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.alreadyInList").value(1));

        mvc.perform(as(ana, get("/api/shopping-list")))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].productId").value(rice))
                .andExpect(jsonPath("$[1].name").value("Arroz Tio João 5 kg"))
                .andExpect(jsonPath("$[1].quantity").value(2))
                .andExpect(jsonPath("$[2].productId").doesNotExist())
                .andExpect(jsonPath("$[2].name").value(startsWith("ITEM")));
    }

    @Test
    void receiptsOfOtherUsersCannotBeUsed() throws Exception {
        long receiptId = data.receipt(anaId, assaiCentro, "2026-09-01",
                new Line(data.storeProduct(assaiCentro, rice), "1", "25.00"));

        mvc.perform(as(bia, post("/api/shopping-list/from-receipt"))
                        .content("{\"receiptId\":" + receiptId + ",\"lineNumbers\":[1]}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(ana, post("/api/shopping-list/from-receipt"))
                        .content("{\"receiptId\":" + receiptId + ",\"lineNumbers\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/shopping-list/products").param("q", "arroz")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/shopping-list/markets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/shopping-list/prices").param("market", "ASSAI")).andExpect(status().isUnauthorized());
    }

    private long product(String normalizedName, String displayName) {
        long id = data.product(normalizedName);
        jdbc.update("UPDATE products SET display_name = ? WHERE id = ?", displayName, id);
        return id;
    }

    private void add(String body) throws Exception {
        mvc.perform(as(ana, post("/api/shopping-list")).content(body)).andExpect(status().isCreated());
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
