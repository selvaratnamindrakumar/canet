package com.canet.diosma.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

@Slf4j
@Configuration
public class ValidatorClientConfig {

    @Value("${validator.ssl.trust-all:false}")
    private boolean trustAll;

    /**
     * RestTemplate that optionally trusts all certificates.
     *
     * trust-all=true  — accepts the validator's self-signed certificate without
     *                   importing it into a truststore (convenient for testing).
     * trust-all=false — uses the JVM default truststore (set javax.net.ssl.trustStore
     *                   to point at the validator's truststore for production).
     */
    @Bean
    public RestTemplate restTemplate() throws Exception {
        if (trustAll) {
            log.warn("validator.ssl.trust-all=true — accepting all TLS certificates. Use only for testing.");
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
        }
        return new RestTemplate();
    }
}
