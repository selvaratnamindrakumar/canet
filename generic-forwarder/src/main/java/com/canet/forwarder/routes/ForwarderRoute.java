package com.canet.forwarder.routes;

import java.io.File;

import javax.net.ssl.HostnameVerifier;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.canet.forwarder.config.ForwarderProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Main Camel route.
 *
 * Flow:
 *   direct:forwarderInput          ← fed by CamelBridge (@ServiceActivator)
 *     → enrich HTTP headers (X-Feed, X-Environment, Content-Type)
 *     → POST to endpoint.url (HTTPS with optional mTLS)
 *
 * The Spring Integration → Camel hand-off is done in {@link CamelBridge}.
 */
@Slf4j
@Component
public class ForwarderRoute extends RouteBuilder {

    private final ForwarderProperties props;
    private final SSLContextParameters outputSslContextParameters;

    public ForwarderRoute(
            ForwarderProperties props,
            @Autowired(required = false)
            @Qualifier("outputSslContextParameters")
            SSLContextParameters outputSslContextParameters) {
        this.props = props;
        this.outputSslContextParameters = outputSslContextParameters;
    }

    @Override
    public void configure() {

        // ── Output SSL on the Camel HTTPS component ──────────────────────
        if (outputSslContextParameters != null) {
            HttpComponent https = getContext().getComponent("https", HttpComponent.class);
            https.setSslContextParameters(outputSslContextParameters);

            // Dev trust-all: also disable hostname verification so that
            // self-signed localhost certs are accepted without complaint.
            if (props.getEndpoint().getSsl().isDevTrustAll()) {
                HostnameVerifier noopVerifier = (hostname, session) -> true;
                https.setX509HostnameVerifier(noopVerifier);
                log.warn("Hostname verification DISABLED (dev-trust-all mode)");
            } else {
                log.info("Output SSL configured on Camel HTTPS component");
            }
        }

        // ── Error / retry policy ─────────────────────────────────────────
        ForwarderProperties.Endpoint ep = props.getEndpoint();
        errorHandler(defaultErrorHandler()
                .maximumRedeliveries(ep.getMaxRetries())
                .redeliveryDelay(ep.getRetryDelayMs())
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .useExponentialBackOff());

        // ── Main route ───────────────────────────────────────────────────
        from("direct:forwarderInput")
            .routeId("forwarder-main")
            .log(LoggingLevel.DEBUG, log.getName(), "Forwarding message to endpoint")

            // File payloads – tell Camel to stream the file body
            .process(exchange -> {
                if (exchange.getIn().getBody() instanceof File file) {
                    exchange.getIn().setHeader(Exchange.FILE_NAME, file.getName());
                }
            })

            // HTTP headers -------------------------------------------------------
            .setHeader(Exchange.HTTP_METHOD, constant("POST"))
            .setHeader("Content-Type",       constant(ep.getContentType()))
            .setHeader("X-Feed",             constant(ep.getFeed()))
            .setHeader("X-Environment",      constant(ep.getEnvironment()))

            // Strip Spring Integration headers before sending out
            .removeHeaders("SI_*")
            .removeHeader("breadcrumbId")

            // Forward to endpoint ------------------------------------------------
            .toD(resolveEndpointUri())

            .log(LoggingLevel.INFO, log.getName(),
                    "Message forwarded → " + ep.getUrl()
                    + " [feed=" + ep.getFeed() + ", env=" + ep.getEnvironment() + "]");
    }

    private String resolveEndpointUri() {
        ForwarderProperties.Endpoint ep = props.getEndpoint();
        String url = ep.getUrl();
        String sep = url.contains("?") ? "&" : "?";
        return url
                + sep
                + "bridgeEndpoint=true"
                + "&throwExceptionOnFailure=true"
                + "&connectTimeout=" + ep.getConnectTimeoutMs()
                + "&socketTimeout="  + ep.getSocketTimeoutMs();
    }
}
