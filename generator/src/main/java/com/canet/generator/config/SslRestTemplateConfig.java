package com.canet.generator.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

/**
 * Builds the RestTemplate used by ValidatorClient and DiosmaClient.
 *
 * Three modes (checked in order):
 *
 * 1. validator.ssl.trust-all=true
 *    Disables certificate verification — use for self-signed certs in test/staging.
 *
 * 2. ssl.keystore.path is set
 *    Mutual TLS: loads keystore + truststore from disk.
 *
 * 3. Neither — plain RestTemplate, suitable for HTTP or when the JVM
 *    trust store already contains the validator certificate.
 */
@Slf4j
@Configuration
public class SslRestTemplateConfig {

    @Value("${validator.ssl.trust-all:false}") private boolean trustAll;

    @Value("${ssl.keystore.path:}")       private String keystorePath;
    @Value("${ssl.keystore.password:}")   private String keystorePassword;
    @Value("${ssl.keystore.type:PKCS12}") private String keystoreType;
    @Value("${ssl.truststore.path:}")     private String truststorePath;
    @Value("${ssl.truststore.password:}") private String truststorePassword;
    @Value("${ssl.truststore.type:PKCS12}") private String truststoreType;

    @Bean
    public RestTemplate restTemplate() throws Exception {

        if (trustAll) {
            log.warn("validator.ssl.trust-all=true — certificate verification disabled (test/staging only)");

            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();

            SSLConnectionSocketFactory sslSocketFactory =
                    new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(
                            PoolingHttpClientConnectionManagerBuilder.create()
                                    .setSSLSocketFactory(sslSocketFactory)
                                    .build())
                    .build();

            return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
        }

        if (keystorePath != null && !keystorePath.isBlank()) {
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

            SSLConnectionSocketFactory sslSocketFactory =
                    new SSLConnectionSocketFactory(sslContext);

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(
                            PoolingHttpClientConnectionManagerBuilder.create()
                                    .setSSLSocketFactory(sslSocketFactory)
                                    .build())
                    .build();

            return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
        }

        log.info("ssl not configured — using default RestTemplate");
        return new RestTemplate();
    }
}
