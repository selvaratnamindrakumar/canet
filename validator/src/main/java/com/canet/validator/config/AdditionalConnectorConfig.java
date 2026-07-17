package com.canet.validator.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdditionalConnectorConfig {

    @Value("${server.http.port:8080}")
    private int httpPort;

    /**
     * When HTTPS is active on server.port (8443) this bean adds a plain HTTP
     * connector on server.http.port (default 8080) so that:
     *   - The generator can POST /api/validator/record over plain HTTP on the
     *     internal network without needing a truststore.
     *   - The health endpoint stays reachable in a browser without a certificate.
     *
     * The middle tier (Diosma) should use the HTTPS port exclusively.
     *
     * To disable this second connector, set server.ssl.enabled=false and
     * change server.port back to 8080 in application.properties.
     */
    @Bean
    @ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpConnector() {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setScheme("http");
            connector.setPort(httpPort);
            connector.setSecure(false);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
