package com.canet.validator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds all diosma.* properties from application.properties.
 *
 * diosma.post-url        — full URL of the Diosma intake endpoint.
 *                          Leave blank to disable notifications.
 *
 * diosma.headers.<name>  — HTTP headers sent with every notification.
 *                          Any number of headers can be specified:
 *
 *   diosma.headers.Content-Type=application/json
 *   diosma.headers.X-Application-Id=canet-validator
 *   diosma.headers.Authorization=Bearer <token>
 */
@Component
@ConfigurationProperties(prefix = "diosma")
public class DiosmaProperties {

    private String postUrl = "";

    /**
     * Free-form header map.  Keys are treated as HTTP header names
     * (case-insensitive); values are sent verbatim.
     * Content-Type defaults to application/json when absent.
     */
    private Map<String, String> headers = new LinkedHashMap<>();

    public String getPostUrl()                        { return postUrl; }
    public void   setPostUrl(String postUrl)          { this.postUrl = postUrl; }
    public Map<String, String> getHeaders()           { return headers; }
    public void   setHeaders(Map<String, String> h)   { this.headers = h; }
}
