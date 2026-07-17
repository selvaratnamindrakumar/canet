package com.canet.udplistener.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;

/**
 * Configures a RestTemplate for outbound HTTPS calls (e.g. generator → validator
 * when deployed as separate services) using mutual TLS via keystore and truststore.
 *
 * When ssl.keystore.path is left empty the bean falls back to a plain RestTemplate
 * with no custom SSL context, suitable for HTTP or for when the JVM default
 * trust store is sufficient.
 *
 * Properties (all optional — set when mutual TLS is required):
 *
 *   ssl.keystore.path      = /etc/canet/keystore.p12
 *   ssl.keystore.password  = changeit
 *   ssl.keystore.type      = PKCS12
 *   ssl.truststore.path    = /etc/canet/truststore.p12
 *   ssl.truststore.password= changeit
 *   ssl.truststore.type    = PKCS12
 *
 * Server-side SSL (so Diosma can reach the validator over HTTPS) is configured
 * separately via the server.ssl.* properties in application.properties.
 */
@Slf4j
@Configuration
public class SslRestTemplateConfig {

    @Value("${ssl.keystore.path:}")
    private String keystorePath;

    @Value("${ssl.keystore.password:}")
    private String keystorePassword;

    @Value("${ssl.keystore.type:PKCS12}")
    private String keystoreType;

    @Value("${ssl.truststore.path:}")
    private String truststorePath;

    @Value("${ssl.truststore.password:}")
    private String truststorePassword;

    @Value("${ssl.truststore.type:PKCS12}")
    private String truststoreType;

    @Bean
    public RestTemplate restTemplate() throws Exception {
        if (keystorePath == null || keystorePath.isBlank()) {
            log.info("ssl.keystore.path not set — using default RestTemplate (no custom SSL context)");
            return new RestTemplate();
        }

        log.info("Building RestTemplate with keystore={} truststore={}", keystorePath, truststorePath);

        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        try (FileInputStream ks = new FileInputStream(keystorePath)) {
            keyStore.load(ks, keystorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePassword.toCharArray());

        KeyStore trustStore = KeyStore.getInstance(truststoreType);
        try (FileInputStream ts = new FileInputStream(truststorePath)) {
            trustStore.load(ts, truststorePassword.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        return new RestTemplate(new JdkClientHttpRequestFactory(httpClient));
    }
}
