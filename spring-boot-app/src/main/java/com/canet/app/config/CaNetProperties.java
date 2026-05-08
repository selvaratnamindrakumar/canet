package com.canet.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "canet")
public class CaNetProperties {

    private List<EndpointMapping> endpoints = new ArrayList<>();

    public List<EndpointMapping> getEndpoints() { return endpoints; }
    public void setEndpoints(List<EndpointMapping> endpoints) { this.endpoints = endpoints; }

    public EndpointMapping findDefault() {
        return endpoints.stream()
                .filter(EndpointMapping::isDefaultEndpoint)
                .findFirst()
                .orElse(endpoints.isEmpty() ? null : endpoints.get(0));
    }

    public static class EndpointMapping {

        private String name = "";
        private String url = "";
        private boolean defaultEndpoint = false;
        /** Fields shown in the compact table row. */
        private List<String> compactFields = new ArrayList<>();
        /** Fields shown in the expandable detail panel. Use ["*"] to mean all remaining fields. */
        private List<String> detailFields = new ArrayList<>();
        /** Human-readable column header overrides, keyed by field name. */
        private Map<String, String> labels = new LinkedHashMap<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public boolean isDefaultEndpoint() { return defaultEndpoint; }
        public void setDefaultEndpoint(boolean defaultEndpoint) { this.defaultEndpoint = defaultEndpoint; }

        public List<String> getCompactFields() { return compactFields; }
        public void setCompactFields(List<String> compactFields) { this.compactFields = compactFields; }

        public List<String> getDetailFields() { return detailFields; }
        public void setDetailFields(List<String> detailFields) { this.detailFields = detailFields; }

        public Map<String, String> getLabels() { return labels; }
        public void setLabels(Map<String, String> labels) { this.labels = labels; }
    }
}
