package com.example.csvprocessor.service;

import com.example.csvprocessor.model.MappingConfiguration;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Loads and caches the field-mapping configuration from {@code mapping.yml}.
 *
 * <p>The YAML file must have a top-level {@code mapping:} key whose value
 * matches {@link MappingConfiguration}.  The file is read once at startup;
 * restart the application to pick up changes.
 *
 * <p>Parsing strategy: SnakeYAML loads the file as a plain
 * {@code Map<String, Object>} (no typed Constructor — compatible with all
 * SnakeYAML versions including 1.30, 1.31, 1.32+).  Jackson's
 * {@link ObjectMapper#convertValue} then maps the sub-map to the typed
 * model classes.  Jackson is already on the classpath via
 * {@code spring-boot-starter}.
 */
@Service
public class MappingConfigService {

    private static final Logger log = LoggerFactory.getLogger(MappingConfigService.class);

    @Value("classpath:mapping.yml")
    private Resource mappingResource;

    private MappingConfiguration configuration;

    @PostConstruct
    public void load() throws IOException {
        log.info("Loading mapping configuration from {}", mappingResource.getDescription());

        // Step 1: load raw YAML into a generic map — no SnakeYAML Constructor needed
        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (InputStream is = mappingResource.getInputStream()) {
            root = yaml.load(is);
        }

        Object mappingNode = root.get("mapping");
        if (mappingNode == null) {
            throw new IllegalStateException("mapping.yml must have a top-level 'mapping:' key");
        }

        // Step 2: convert the sub-map to the typed configuration via Jackson
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        configuration = mapper.convertValue(mappingNode, MappingConfiguration.class);

        validateConfiguration();
        log.info("Mapping configuration loaded: {} field(s) defined, outputFormat={}",
                configuration.getFields().size(), configuration.getOutputFormat());
    }

    private void validateConfiguration() {
        if (configuration.getFields() == null || configuration.getFields().isEmpty()) {
            throw new IllegalStateException("mapping.yml defines no fields");
        }
        configuration.getFields().forEach(f -> {
            if (f.getTargetColumnName() == null || f.getTargetColumnName().isBlank()) {
                throw new IllegalStateException("Every field mapping must have a targetColumnName");
            }
        });
    }

    public MappingConfiguration getConfiguration() {
        return configuration;
    }
}
