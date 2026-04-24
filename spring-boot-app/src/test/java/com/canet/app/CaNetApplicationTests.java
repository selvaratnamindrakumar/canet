package com.canet.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.canet.app.service.HttpsDataService;

/**
 * Smoke test: verifies the Spring context loads without hitting the real API.
 */
@SpringBootTest
class CaNetApplicationTests {

    // Stub the service so no real HTTPS call is made during context load
    @MockBean
    HttpsDataService dataService;

    @Test
    void contextLoads() {
        // passes if the application context starts without errors
    }
}
