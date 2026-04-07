package com.canet.forwarder.config;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds Camel {@link SSLContextParameters} for the outbound HTTPS connection.
 *
 * Two modes (mutually exclusive, selected by properties):
 *
 *  1. endpoint.ssl.enabled=true, endpoint.ssl.dev-trust-all=false
 *     → proper mTLS using configured keystore / truststore files
 *
 *  2. endpoint.ssl.enabled=true, endpoint.ssl.dev-trust-all=true
 *     → trust-all context (hostname + cert validation disabled)
 *     → DEV / local testing only — never use in production
 *
 * Input-side SSL (Kafka / RabbitMQ) is configured directly in the respective
 * integration config classes using native client properties.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SslContextConfig {

    private final ForwarderProperties props;

    @Bean("outputSslContextParameters")
    @ConditionalOnProperty(prefix = "endpoint.ssl", name = "enabled", havingValue = "true")
    public SSLContextParameters outputSslContextParameters() {
        ForwarderProperties.Ssl ssl = props.getEndpoint().getSsl();

        if (ssl.isDevTrustAll()) {
            log.warn("*************************************************************");
            log.warn("*** endpoint.ssl.dev-trust-all=true                       ***");
            log.warn("*** Certificate and hostname validation are DISABLED.      ***");
            log.warn("*** For local/dev testing only — never use in production.  ***");
            log.warn("*************************************************************");
            return buildTrustAllContext();
        }

        return buildSslContextParameters(ssl);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Proper mTLS context using configured keystore / truststore paths. */
    static SSLContextParameters buildSslContextParameters(ForwarderProperties.Ssl ssl) {
        SSLContextParameters scp = new SSLContextParameters();

        if (ssl.getKeystorePath() != null && !ssl.getKeystorePath().isBlank()) {
            KeyStoreParameters ksp = new KeyStoreParameters();
            ksp.setResource(ssl.getKeystorePath());
            ksp.setPassword(ssl.getKeystorePassword());
            ksp.setType(ssl.getKeystoreType());

            KeyManagersParameters kmp = new KeyManagersParameters();
            kmp.setKeyStore(ksp);
            kmp.setKeyPassword(ssl.getKeystorePassword());
            scp.setKeyManagers(kmp);
        }

        if (ssl.getTruststorePath() != null && !ssl.getTruststorePath().isBlank()) {
            KeyStoreParameters tsp = new KeyStoreParameters();
            tsp.setResource(ssl.getTruststorePath());
            tsp.setPassword(ssl.getTruststorePassword());
            tsp.setType(ssl.getTruststoreType());

            TrustManagersParameters tmp = new TrustManagersParameters();
            tmp.setKeyStore(tsp);
            scp.setTrustManagers(tmp);
        }

        return scp;
    }

    /**
     * DEV ONLY trust-all context — accepts any server certificate and any
     * hostname.  Also disables hostname checking on the Camel HTTP component
     * (see {@link com.canet.forwarder.routes.ForwarderRoute#configure()}).
     */
    private static SSLContextParameters buildTrustAllContext() {
        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setTrustManager(new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] c, String a) {}
            @Override public void checkServerTrusted(X509Certificate[] c, String a) {}
            @Override public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        });

        SSLContextParameters scp = new SSLContextParameters();
        scp.setTrustManagers(tmp);
        return scp;
    }
}
