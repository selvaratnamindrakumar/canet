package com.canet.forwarder.config;

import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

/**
 * Builds Camel {@link SSLContextParameters} for the outbound HTTPS connection.
 * Input-side SSL (Kafka / RabbitMQ) is configured directly in their respective
 * integration config classes using native client properties.
 */
@Configuration
@RequiredArgsConstructor
public class SslContextConfig {

    private final ForwarderProperties props;

    /**
     * Output SSL context – used by Camel's HTTP component when posting to the
     * HTTPS endpoint.  Only created when {@code endpoint.ssl.enabled=true}.
     */
    @Bean("outputSslContextParameters")
    @ConditionalOnProperty(prefix = "endpoint.ssl", name = "enabled", havingValue = "true")
    public SSLContextParameters outputSslContextParameters() {
        ForwarderProperties.Ssl ssl = props.getEndpoint().getSsl();
        return buildSslContextParameters(ssl);
    }

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
}
