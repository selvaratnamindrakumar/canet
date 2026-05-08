package com.canet.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for HttpsDataServiceImpl using Spring's MockRestServiceServer.
 *
 * Uses handset-shaped JSON to verify the service works with any field names —
 * the generic Map approach means these tests would still pass if the API
 * schema added/removed fields like tac, marketingName, manufacturer, etc.
 *
 * No real network traffic leaves the JVM — suitable for CI pipelines.
 */
@RestClientTest(HttpsDataServiceImpl.class)
class HttpsDataServiceTest {

    @Autowired
    HttpsDataServiceImpl service;

    @Autowired
    MockRestServiceServer server;

    private static final String BASE_URL = "https://mock.example.com/handsets";

    @BeforeEach
    void reset() {
        server.reset();
    }

    // -----------------------------------------------------------------------
    // fetchAll(url)
    // -----------------------------------------------------------------------

    @Test
    void fetchAll_parsesHandsetJsonIntoMaps() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withSuccess(handsetListJson(), MediaType.APPLICATION_JSON));

        List<Map<String, Object>> result = service.fetchAll(BASE_URL);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("tac", "35674108")
                                  .containsEntry("marketingName", "Galaxy S24")
                                  .containsEntry("manufacturer", "Samsung");
        assertThat(result.get(1)).containsEntry("marketingName", "iPhone 15 Pro");
        server.verify();
    }

    @Test
    void fetchAll_picksUpNewFieldsWithoutCodeChange() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withSuccess(handsetWithNewFieldJson(), MediaType.APPLICATION_JSON));

        List<Map<String, Object>> result = service.fetchAll(BASE_URL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsKey("bandSupport");
        assertThat(result.get(0).get("bandSupport")).isEqualTo("5G SA/NSA");
        server.verify();
    }

    @Test
    void fetchAll_whenApiReturnsEmptyArray_returnsEmptyList() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(service.fetchAll(BASE_URL)).isEmpty();
        server.verify();
    }

    @Test
    void fetchAll_whenServerErrors_returnsEmptyListGracefully() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withServerError());

        assertThat(service.fetchAll(BASE_URL)).isEmpty();
        server.verify();
    }

    @Test
    void fetchAll_whenServiceUnavailable_returnsEmptyListGracefully() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withServiceUnavailable());

        assertThat(service.fetchAll(BASE_URL)).isEmpty();
        server.verify();
    }

    // -----------------------------------------------------------------------
    // fetchById(url, id)
    // -----------------------------------------------------------------------

    @Test
    void fetchById_returnsSingleHandsetRecord() {
        server.expect(requestTo(BASE_URL + "/35674108"))
              .andRespond(withSuccess(singleHandsetJson(), MediaType.APPLICATION_JSON));

        Map<String, Object> record = service.fetchById(BASE_URL, "35674108");

        assertThat(record).isNotNull();
        assertThat(record).containsEntry("tac", "35674108")
                          .containsEntry("marketingName", "Galaxy S24")
                          .containsEntry("manufacturer", "Samsung");
        server.verify();
    }

    @Test
    void fetchById_whenNotFound_returnsNull() {
        server.expect(requestTo(BASE_URL + "/00000000"))
              .andRespond(withResourceNotFound());

        assertThat(service.fetchById(BASE_URL, "00000000")).isNull();
        server.verify();
    }

    // -----------------------------------------------------------------------
    // JSON fixtures — use handset data to reflect real intended usage
    // -----------------------------------------------------------------------

    private String handsetListJson() {
        return """
                [
                  {"tac":"35674108","marketingName":"Galaxy S24","manufacturer":"Samsung","modelName":"SM-S921"},
                  {"tac":"35899234","marketingName":"iPhone 15 Pro","manufacturer":"Apple","modelName":"A3293"}
                ]
                """;
    }

    private String handsetWithNewFieldJson() {
        return """
                [
                  {"tac":"35674108","marketingName":"Galaxy S24","manufacturer":"Samsung","bandSupport":"5G SA/NSA"}
                ]
                """;
    }

    private String singleHandsetJson() {
        return """
                {"tac":"35674108","marketingName":"Galaxy S24","manufacturer":"Samsung","modelName":"SM-S921"}
                """;
    }
}
