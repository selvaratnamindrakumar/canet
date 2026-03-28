package com.canet.forwarder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test – verifies the application context loads with source.type=file.
 * No external systems required.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "source.type=file",
        "source.file.directory=/tmp/forwarder-test",
        "endpoint.url=http://localhost:9999/ingest",
        "endpoint.feed=test-feed",
        "endpoint.environment=test",
        "endpoint.ssl.enabled=false",
        "source.ssl.enabled=false",
        "camel.springboot.main-run-controller=false"
})
class ForwarderApplicationTest {

    @Test
    void contextLoads() {
        // Context starts successfully with file source
    }
}
