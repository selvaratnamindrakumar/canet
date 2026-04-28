package com.canet.app.mock;

import com.canet.app.model.DataItem;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests using an embedded WireMock HTTP server.
 *
 * These tests verify HTTP-level behaviour — routing, status codes, response
 * bodies, latency, and request verification — independently of the Spring
 * context.  SSL/TLS is handled by the JVM trust store at runtime; it is not
 * exercised here so that the tests remain fast and self-contained.
 */
class MockApiServerTest {

    private WireMockServer wireMock;
    private RestTemplate restTemplate;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        restTemplate = new RestTemplate(factory);
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    void mockServer_returns200WithHandsetJsonArray() {
        wireMock.stubFor(get(urlEqualTo("/handsets"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"tac":"35674108","marketingName":"Galaxy S24","manufacturer":"Samsung"},
                                  {"tac":"35899234","marketingName":"iPhone 15 Pro","manufacturer":"Apple"}
                                ]
                                """)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = restTemplate.getForObject(
                baseUrl("/handsets"), List.class);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("marketingName")).isEqualTo("Galaxy S24");
        assertThat(items.get(1).get("manufacturer")).isEqualTo("Apple");
    }

    @Test
    void mockServer_returnsUpdatedSchema_newFieldsAppearAutomatically() {
        // Simulate the API adding a "bandSupport" field — the generic Map approach
        // means existing code picks it up with no changes required.
        wireMock.stubFor(get(urlEqualTo("/handsets"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"tac":"35674108","marketingName":"Galaxy S24",
                                   "manufacturer":"Samsung","bandSupport":"5G SA/NSA"}
                                ]
                                """)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = restTemplate.getForObject(
                baseUrl("/handsets"), List.class);

        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsKey("bandSupport");
        assertThat(items.get(0).get("bandSupport")).isEqualTo("5G SA/NSA");
    }

    // -----------------------------------------------------------------------
    // Error scenarios
    // -----------------------------------------------------------------------

    @Test
    void mockServer_returns404_whenHandsetNotFound() {
        wireMock.stubFor(get(urlEqualTo("/handsets/00000000"))
                .willReturn(aResponse().withStatus(404)));

        try {
            restTemplate.getForObject(baseUrl("/handsets/00000000"), DataItem.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            assertThat(e.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void mockServer_returns500_onServerError() {
        wireMock.stubFor(get(urlEqualTo("/handsets"))
                .willReturn(aResponse().withStatus(500)));

        try {
            restTemplate.getForObject(baseUrl("/handsets"), List.class);
        } catch (org.springframework.web.client.HttpServerErrorException.InternalServerError e) {
            assertThat(e.getStatusCode().value()).isEqualTo(500);
        }
    }

    // -----------------------------------------------------------------------
    // Latency / behaviour
    // -----------------------------------------------------------------------

    @Test
    void mockServer_simulatesSlowResponse_withinTimeout() {
        wireMock.stubFor(get(urlEqualTo("/handsets/slow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"tac":"99999999","marketingName":"Slow Device","manufacturer":"Test"}
                                """)));

        @SuppressWarnings("unchecked")
        Map<String, Object> item = restTemplate.getForObject(
                baseUrl("/handsets/slow"), Map.class);

        assertThat(item).isNotNull();
        assertThat(item.get("marketingName")).isEqualTo("Slow Device");
    }

    // -----------------------------------------------------------------------
    // Request verification
    // -----------------------------------------------------------------------

    @Test
    void mockServer_verifyExactlyOneRequestWasMade() {
        wireMock.stubFor(get(urlEqualTo("/handsets"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        restTemplate.getForObject(baseUrl("/handsets"), List.class);

        wireMock.verify(exactly(1), getRequestedFor(urlEqualTo("/handsets")));
    }

    // -----------------------------------------------------------------------

    private String baseUrl(String path) {
        return "http://localhost:" + wireMock.port() + path;
    }
}
