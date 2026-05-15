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

    public EndpointMapping findByType(String type) {
        return endpoints.stream()
                .filter(e -> type.equals(e.getType()))
                .findFirst()
                .orElse(null);
    }

    public static class EndpointMapping {

        private String name = "";
        private String url = "";
        /** Logical search type: "handset" or "cell". */
        private String type = "handset";
        private boolean defaultEndpoint = false;
        /** Fields shown in the compact table row. */
        private List<String> compactFields = new ArrayList<>();
        /** Fields shown in the expandable detail panel. Use ["*"] to mean all remaining fields. */
        private List<String> detailFields = new ArrayList<>();
        /** Human-readable column header overrides, keyed by field name. */
        private Map<String, String> labels = new LinkedHashMap<>();
        /** Whether to flatten nested JSON objects using flattenSeparator. */
        private boolean flatten = false;
        /** Separator inserted between parent and child key when flattening. */
        private String flattenSeparator = "-";
        /** Seconds the submit button stays disabled after the page loads. 0 = no delay. */
        private int submitDelaySeconds = 0;
        /** Client-side regex pattern used to validate each input entry. */
        private String validationPattern = "";
        /** Error message shown when validationPattern is not satisfied. */
        private String validationMessage = "Invalid input";
        /** Maximum number of entries accepted per search (HANDSET_SEARCH_CONFIG / CELL_SEARCH_CONFIG). */
        private int maxEntries = 20;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public boolean isDefaultEndpoint() { return defaultEndpoint; }
        public void setDefaultEndpoint(boolean defaultEndpoint) { this.defaultEndpoint = defaultEndpoint; }

        public List<String> getCompactFields() { return compactFields; }
        public void setCompactFields(List<String> compactFields) { this.compactFields = compactFields; }

        public List<String> getDetailFields() { return detailFields; }
        public void setDetailFields(List<String> detailFields) { this.detailFields = detailFields; }

        public Map<String, String> getLabels() { return labels; }
        public void setLabels(Map<String, String> labels) { this.labels = labels; }

        public boolean isFlatten() { return flatten; }
        public void setFlatten(boolean flatten) { this.flatten = flatten; }

        public String getFlattenSeparator() { return flattenSeparator; }
        public void setFlattenSeparator(String flattenSeparator) { this.flattenSeparator = flattenSeparator; }

        public int getSubmitDelaySeconds() { return submitDelaySeconds; }
        public void setSubmitDelaySeconds(int submitDelaySeconds) { this.submitDelaySeconds = submitDelaySeconds; }

        public String getValidationPattern() { return validationPattern; }
        public void setValidationPattern(String validationPattern) { this.validationPattern = validationPattern; }

        public String getValidationMessage() { return validationMessage; }
        public void setValidationMessage(String validationMessage) { this.validationMessage = validationMessage; }

        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    }
}
