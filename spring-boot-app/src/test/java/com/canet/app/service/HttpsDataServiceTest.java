package com.canet.app.service;

import com.canet.app.model.DataItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for HttpsDataServiceImpl using Spring's MockRestServiceServer.
 *
 * MockRestServiceServer intercepts RestTemplate calls so NO real network
 * traffic leaves the JVM — suitable for CI pipelines with no internet access.
 */
@RestClientTest(HttpsDataServiceImpl.class)
@TestPropertySource(properties = "external.api.base-url=https://mock.example.com/todos")
class HttpsDataServiceTest {

    @Autowired
    HttpsDataServiceImpl service;

    @Autowired
    MockRestServiceServer server;

    private static final String BASE_URL = "https://mock.example.com/todos";

    @BeforeEach
    void reset() {
        server.reset();
    }

    // -----------------------------------------------------------------------
    // fetchAll()
    // -----------------------------------------------------------------------

    @Test
    void fetchAll_parsesJsonArrayIntoList() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withSuccess(itemsJson(), MediaType.APPLICATION_JSON));

        List<DataItem> result = service.fetchAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Do the laundry");
        assertThat(result.get(0).isCompleted()).isTrue();
        assertThat(result.get(1).isCompleted()).isFalse();

        server.verify();
    }

    @Test
    void fetchAll_whenApiReturnsEmptyArray_returnsEmptyList() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(service.fetchAll()).isEmpty();
        server.verify();
    }

    @Test
    void fetchAll_whenServerErrors_returnsEmptyListGracefully() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withServerError());

        // The service swallows the error and returns an empty list
        assertThat(service.fetchAll()).isEmpty();
        server.verify();
    }

    @Test
    void fetchAll_whenServiceUnavailable_returnsEmptyListGracefully() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withServiceUnavailable());

        assertThat(service.fetchAll()).isEmpty();
        server.verify();
    }

    // -----------------------------------------------------------------------
    // fetchById()
    // -----------------------------------------------------------------------

    @Test
    void fetchById_returnsSingleItem() {
        server.expect(requestTo(BASE_URL + "/1"))
              .andRespond(withSuccess(singleItemJson(), MediaType.APPLICATION_JSON));

        DataItem item = service.fetchById(1);

        assertThat(item).isNotNull();
        assertThat(item.getId()).isEqualTo(1);
        assertThat(item.getTitle()).isEqualTo("Do the laundry");
        assertThat(item.isCompleted()).isTrue();
        server.verify();
    }

    @Test
    void fetchById_whenNotFound_returnsNull() {
        server.expect(requestTo(BASE_URL + "/999"))
              .andRespond(withResourceNotFound());

        assertThat(service.fetchById(999)).isNull();
        server.verify();
    }

    // -----------------------------------------------------------------------
    // JSON helpers
    // -----------------------------------------------------------------------

    private String itemsJson() {
        return """
                [
                  {"userId":1,"id":1,"title":"Do the laundry","completed":true},
                  {"userId":1,"id":2,"title":"Buy groceries","completed":false}
                ]
                """;
    }

    private String singleItemJson() {
        return """
                {"userId":1,"id":1,"title":"Do the laundry","completed":true}
                """;
    }
}
