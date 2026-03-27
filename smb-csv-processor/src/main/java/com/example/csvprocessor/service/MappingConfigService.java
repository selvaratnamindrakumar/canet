package com.example.csvprocessor.service;

import com.example.csvprocessor.model.MappingConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Loads and caches the field-mapping configuration from {@code mapping.yml}.
 *
 * <p>The YAML file is expected to have a top-level {@code mapping:} key whose
 * value matches {@link MappingConfiguration}.  The file is read once at startup;
 * restart the application to pick up changes.
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

        Yaml yaml = new Yaml();
        try (InputStream is = mappingResource.getInputStream()) {
            // The YAML has a top-level "mapping:" wrapper key
            Map<String, Object> root = yaml.load(is);
            Object mappingNode = root.get("mapping");
            if (mappingNode == null) {
                throw new IllegalStateException("mapping.yml must have a top-level 'mapping:' key");
            }

            // Re-serialise the sub-map and parse into the typed class
            Yaml typedYaml = new Yaml(new Constructor(MappingConfiguration.class));
            String subYaml = new Yaml().dump(mappingNode);
            configuration = typedYaml.load(subYaml);
        }

        validateConfiguration();
        log.info("Mapping configuration loaded: {} fields defined", configuration.getFields().size());
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
