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
 * Produces two RestTemplate beans — one per downstream service — so each
 * can be configured independently for SSL:
 *
 *   validatorRestTemplate  →  ValidatorClient   (validator.ssl.*)
 *   diosmaRestTemplate     →  DiosmaClient      (diosma.ssl.*)
 *
 * Each bean supports three modes (checked in order):
 *   1. *.ssl.trust-all=true          — skip cert verification (test/staging)
 *   2. *.ssl.keystore.path set       — mutual TLS with keystore + truststore
 *   3. neither                       — plain RestTemplate (HTTP or JVM trust store)
 */
@Slf4j
@Configuration
public class SslRestTemplateConfig {

    // ─── Validator SSL ────────────────────────────────────────────────
    @Value("${validator.ssl.trust-all:false}")      private boolean validatorTrustAll;
    @Value("${validator.ssl.keystore.path:}")       private String  validatorKeystorePath;
    @Value("${validator.ssl.keystore.password:}")   private String  validatorKeystorePassword;
    @Value("${validator.ssl.keystore.type:PKCS12}") private String  validatorKeystoreType;
    @Value("${validator.ssl.truststore.path:}")     private String  validatorTruststorePath;
    @Value("${validator.ssl.truststore.password:}") private String  validatorTruststorePassword;
    @Value("${validator.ssl.truststore.type:PKCS12}") private String validatorTruststoreType;

    // ─── Diosma SSL ───────────────────────────────────────────────────
    @Value("${diosma.ssl.trust-all:false}")         private boolean diosmaTrustAll;
    @Value("${diosma.ssl.keystore.path:}")          private String  diosmaKeystorePath;
    @Value("${diosma.ssl.keystore.password:}")      private String  diosmaKeystorePassword;
    @Value("${diosma.ssl.keystore.type:PKCS12}")    private String  diosmaKeystoreType;
    @Value("${diosma.ssl.truststore.path:}")        private String  diosmaTruststorePath;
    @Value("${diosma.ssl.truststore.password:}")    private String  diosmaTruststorePassword;
    @Value("${diosma.ssl.truststore.type:PKCS12}")  private String  diosmaTruststoreType;

    @Bean("validatorRestTemplate")
    public RestTemplate validatorRestTemplate() throws Exception {
        return build("validator",
                validatorTrustAll,
                validatorKeystorePath, validatorKeystorePassword, validatorKeystoreType,
                validatorTruststorePath, validatorTruststorePassword, validatorTruststoreType);
    }

    @Bean("diosmaRestTemplate")
    public RestTemplate diosmaRestTemplate() throws Exception {
        return build("diosma",
                diosmaTrustAll,
                diosmaKeystorePath, diosmaKeystorePassword, diosmaKeystoreType,
                diosmaTruststorePath, diosmaTruststorePassword, diosmaTruststoreType);
    }

    private RestTemplate build(String label,
                                boolean trustAll,
                                String keystorePath,   String keystorePassword,   String keystoreType,
                                String truststorePath, String truststorePassword, String truststoreType)
            throws Exception {

        if (trustAll) {
            log.warn("{}.ssl.trust-all=true — certificate verification disabled (test/staging only)", label);
            javax.net.ssl.SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
            SSLConnectionSocketFactory sf =
                    new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
            CloseableHttpClient client = HttpClients.custom()
                    .setConnectionManager(
                            PoolingHttpClientConnectionManagerBuilder.create()
                                    .setSSLSocketFactory(sf).build())
                    .build();
            return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
        }

        if (keystorePath != null && !keystorePath.isBlank()) {
            log.info("{} RestTemplate: loading keystore={} truststore={}", label, keystorePath, truststorePath);

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

            SSLConnectionSocketFactory sf = new SSLConnectionSocketFactory(sslContext);
            CloseableHttpClient client = HttpClients.custom()
                    .setConnectionManager(
                            PoolingHttpClientConnectionManagerBuilder.create()
                                    .setSSLSocketFactory(sf).build())
                    .build();
            return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
        }

        log.info("{} ssl not configured — using default RestTemplate", label);
        return new RestTemplate();
    }
}
