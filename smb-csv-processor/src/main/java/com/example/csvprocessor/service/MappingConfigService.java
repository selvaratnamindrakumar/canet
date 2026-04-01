package com.example.csvprocessor.service;

import com.example.csvprocessor.model.FieldMapping;
import com.example.csvprocessor.model.InputFieldRef;
import com.example.csvprocessor.model.MappingConfiguration;
import com.example.csvprocessor.model.RangeRule;
import com.example.csvprocessor.model.ValidationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loads and caches the field-mapping configuration from {@code mapping.yml}.
 *
 * <p>The YAML file must have a top-level {@code mapping:} key whose value
 * matches {@link MappingConfiguration}.  The file is read once at startup;
 * restart the application to pick up changes.
 *
 * <p>Parsing strategy: SnakeYAML loads the file as a generic
 * {@code Map<String, Object>} tree, which is then walked manually to populate
 * the typed model classes.  This approach requires only {@code snakeyaml}
 * (already managed by spring-boot-starter-parent) and no additional libraries.
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
        Map<String, Object> root;
        try (InputStream is = mappingResource.getInputStream()) {
            root = yaml.load(is);
        }

        Object mappingNode = root.get("mapping");
        if (mappingNode == null) {
            throw new IllegalStateException("mapping.yml must have a top-level 'mapping:' key");
        }
        if (!(mappingNode instanceof Map)) {
            throw new IllegalStateException("mapping.yml 'mapping:' key must be a YAML object");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> mappingMap = (Map<String, Object>) mappingNode;
        configuration = toMappingConfiguration(mappingMap);

        validateConfiguration();
        log.info("Mapping configuration loaded: {} field(s) defined, outputFormat={}",
                configuration.getFields().size(), configuration.getOutputFormat());
    }

    // -------------------------------------------------------------------------
    // Manual YAML-map → typed-model converters
    // (no Jackson required — only snakeyaml on classpath)
    // -------------------------------------------------------------------------

    private MappingConfiguration toMappingConfiguration(Map<String, Object> m) {
        MappingConfiguration cfg = new MappingConfiguration();
        if (m.containsKey("inputDelimiter"))  cfg.setInputDelimiter(str(m, "inputDelimiter"));
        if (m.containsKey("outputDelimiter")) cfg.setOutputDelimiter(str(m, "outputDelimiter"));
        if (m.containsKey("hasHeader"))       cfg.setHasHeader(bool(m, "hasHeader"));
        if (m.containsKey("encoding"))        cfg.setEncoding(str(m, "encoding"));
        if (m.containsKey("skipLines"))       cfg.setSkipLines(intVal(m, "skipLines"));
        if (m.containsKey("outputFormat"))    cfg.setOutputFormat(str(m, "outputFormat"));

        Object fieldsNode = m.get("fields");
        if (fieldsNode instanceof List) {
            List<FieldMapping> fields = new ArrayList<>();
            for (Object item : (List<?>) fieldsNode) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fieldMap = (Map<String, Object>) item;
                    fields.add(toFieldMapping(fieldMap));
                }
            }
            cfg.setFields(fields);
        }
        return cfg;
    }

    private FieldMapping toFieldMapping(Map<String, Object> m) {
        FieldMapping f = new FieldMapping();
        if (m.containsKey("sourceColumnName"))  f.setSourceColumnName(str(m, "sourceColumnName"));
        if (m.containsKey("sourceColumnIndex")) f.setSourceColumnIndex(intVal(m, "sourceColumnIndex"));
        if (m.containsKey("targetColumnName"))  f.setTargetColumnName(str(m, "targetColumnName"));
        if (m.containsKey("formula"))           f.setFormula(str(m, "formula"));
        if (m.containsKey("expectedType"))      f.setExpectedType(str(m, "expectedType"));
        if (m.containsKey("nullable"))          f.setNullable(bool(m, "nullable"));
        if (m.containsKey("required"))          f.setRequired(bool(m, "required"));
        if (m.containsKey("defaultValue"))      f.setDefaultValue(str(m, "defaultValue"));

        Object tList = m.get("transformations");
        if (tList instanceof List) {
            List<String> trans = new ArrayList<>();
            for (Object t : (List<?>) tList) {
                if (t != null) trans.add(t.toString());
            }
            f.setTransformations(trans);
        }

        Object inputList = m.get("inputFields");
        if (inputList instanceof List) {
            List<InputFieldRef> refs = new ArrayList<>();
            for (Object item : (List<?>) inputList) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> refMap = (Map<String, Object>) item;
                    InputFieldRef ref = new InputFieldRef();
                    if (refMap.containsKey("name"))  ref.setName(str(refMap, "name"));
                    if (refMap.containsKey("index")) ref.setIndex(intVal(refMap, "index"));
                    refs.add(ref);
                }
            }
            f.setInputFields(refs);
        } else {
            f.setInputFields(Collections.emptyList());
        }

        Object validationNode = m.get("validation");
        if (validationNode instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> vMap = (Map<String, Object>) validationNode;
            f.setValidation(toValidationRule(vMap));
        }

        return f;
    }

    private ValidationRule toValidationRule(Map<String, Object> m) {
        ValidationRule v = new ValidationRule();
        if (m.containsKey("pattern"))    v.setPattern(str(m, "pattern"));
        if (m.containsKey("minValue"))   v.setMinValue(str(m, "minValue"));
        if (m.containsKey("maxValue"))   v.setMaxValue(str(m, "maxValue"));
        if (m.containsKey("minLength"))  v.setMinLength(intVal(m, "minLength"));
        if (m.containsKey("maxLength"))  v.setMaxLength(intVal(m, "maxLength"));

        Object avNode = m.get("allowedValues");
        if (avNode instanceof List) {
            List<String> av = new ArrayList<>();
            for (Object item : (List<?>) avNode) {
                if (item != null) av.add(item.toString());
            }
            v.setAllowedValues(av);
        }

        Object rangeNode = m.get("range");
        if (rangeNode instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rMap = (Map<String, Object>) rangeNode;
            RangeRule rr = new RangeRule();
            if (rMap.containsKey("min")) rr.setMin(str(rMap, "min"));
            if (rMap.containsKey("max")) rr.setMax(str(rMap, "max"));
            v.setRange(rr);
        }
        return v;
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Map access helpers
    // -------------------------------------------------------------------------

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static boolean bool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return v != null && Boolean.parseBoolean(v.toString());
    }

    private static int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString().trim()); }
            catch (NumberFormatException ignored) { /* fall through */ }
        }
        return -1;
    }
}
