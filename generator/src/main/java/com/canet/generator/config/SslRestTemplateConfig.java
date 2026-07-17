package com.canet.generator.config;

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
 * Builds the RestTemplate used by ValidatorClient.
 *
 * When ssl.keystore.path is blank the bean returns a plain RestTemplate
 * (suitable for HTTP or environments where the JVM trust store is sufficient).
 *
 * When ssl.keystore.path is set, a mutual-TLS RestTemplate is built using
 * the supplied keystore and truststore — replacing the previous pattern of
 * disabling SSL verification entirely.
 *
 * Properties (all optional):
 *   ssl.keystore.path       = /etc/canet/keystore.p12
 *   ssl.keystore.password   = changeit
 *   ssl.keystore.type       = PKCS12
 *   ssl.truststore.path     = /etc/canet/truststore.p12
 *   ssl.truststore.password = changeit
 *   ssl.truststore.type     = PKCS12
 */
@Slf4j
@Configuration
public class SslRestTemplateConfig {

    @Value("${ssl.keystore.path:}")       private String keystorePath;
    @Value("${ssl.keystore.password:}")   private String keystorePassword;
    @Value("${ssl.keystore.type:PKCS12}") private String keystoreType;
    @Value("${ssl.truststore.path:}")     private String truststorePath;
    @Value("${ssl.truststore.password:}") private String truststorePassword;
    @Value("${ssl.truststore.type:PKCS12}") private String truststoreType;

    @Bean
    public RestTemplate restTemplate() throws Exception {
        if (keystorePath == null || keystorePath.isBlank()) {
            log.info("ssl.keystore.path not configured — using default RestTemplate");
            return new RestTemplate();
        }

        log.info("RestTemplate: loading keystore={} truststore={}", keystorePath, truststorePath);

        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        try (FileInputStream in = new FileInputStream(keystorePath)) {
            keyStore.load(in, keystorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePassword.toCharArray());

        KeyStore trustStore = KeyStore.getInstance(truststoreType);
        try (FileInputStream in = new FileInputStream(truststorePath)) {
            trustStore.load(in, truststorePassword.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        HttpClient httpClient = HttpClient.newBuilder().sslContext(sslContext).build();
        return new RestTemplate(new JdkClientHttpRequestFactory(httpClient));
    }
}
