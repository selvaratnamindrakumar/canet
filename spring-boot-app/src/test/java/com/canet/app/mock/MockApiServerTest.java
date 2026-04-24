package com.canet.app.mock;

import com.canet.app.model.DataItem;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests using an embedded WireMock HTTP(S) server.
 *
 * This simulates a real external HTTPS endpoint — useful for verifying
 * SSL handshakes, response-code handling, latency, and JSON parsing
 * without a live network dependency.
 *
 * WireMock boots its own HTTPS listener with a self-signed cert on a
 * random port; the RestTemplate is configured to trust that cert via
 * WireMock's built-in trust store.
 */
class MockApiServerTest {

    private WireMockServer wireMock;
    private RestTemplate restTemplate;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(
                WireMockConfiguration.wireMockConfig()
                        .httpsPort(0)        // random free port
                        .httpDisabled(true)  // HTTPS only
        );
        wireMock.start();

        // Use WireMock's bundled trust store so the self-signed cert is accepted
        System.setProperty("javax.net.ssl.trustStore",
                WireMockConfiguration.wireMockConfig().httpsKeystorePath() == null
                        ? "" : WireMockConfiguration.wireMockConfig().httpsKeystorePath());

        restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    @Test
    void mockServer_returns200WithJsonArray() {
        wireMock.stubFor(get(urlEqualTo("/todos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"userId":1,"id":1,"title":"Mock task","completed":false}
                                ]
                                """)));

        String url = "http://localhost:" + wireMock.port() + "/todos";
        DataItem[] items = restTemplate.getForObject(url, DataItem[].class);

        assertThat(items).hasSize(1);
        assertThat(items[0].getTitle()).isEqualTo("Mock task");
        assertThat(items[0].isCompleted()).isFalse();
    }

    @Test
    void mockServer_returns404_whenItemMissing() {
        wireMock.stubFor(get(urlEqualTo("/todos/999"))
                .willReturn(aResponse().withStatus(404)));

        String url = "http://localhost:" + wireMock.port() + "/todos/999";

        try {
            restTemplate.getForObject(url, DataItem.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            assertThat(e.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void mockServer_simulatesSlowResponse() {
        wireMock.stubFor(get(urlEqualTo("/todos/slow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(200)   // ms — well within 5s read timeout
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"userId":1,"id":99,"title":"Slow task","completed":true}
                                """)));

        String url = "http://localhost:" + wireMock.port() + "/todos/slow";
        DataItem item = restTemplate.getForObject(url, DataItem.class);
        assertThat(item).isNotNull();
        assertThat(item.getTitle()).isEqualTo("Slow task");
    }

    @Test
    void mockServer_simulatesServerError() {
        wireMock.stubFor(get(urlEqualTo("/todos"))
                .willReturn(aResponse().withStatus(500)));

        String url = "http://localhost:" + wireMock.port() + "/todos";
        try {
            restTemplate.getForObject(url, List.class);
        } catch (org.springframework.web.client.HttpServerErrorException.InternalServerError e) {
            assertThat(e.getStatusCode().value()).isEqualTo(500);
        }
    }

    @Test
    void mockServer_verifyRequestWasMade() {
        wireMock.stubFor(get(urlEqualTo("/todos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        restTemplate.getForObject("http://localhost:" + wireMock.port() + "/todos", DataItem[].class);

        wireMock.verify(exactly(1), getRequestedFor(urlEqualTo("/todos")));
    }
}
