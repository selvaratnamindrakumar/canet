package com.canet.fileproc.pipeline;

import com.canet.fileproc.model.IngestRecord;
import com.canet.fileproc.model.ValidationResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ValidationPipeline implements Predicate<IngestRecord> {

    private final Map<String, Predicate<IngestRecord>> validators;

    private ValidationPipeline(Map<String, Predicate<IngestRecord>> validators) {
        this.validators = validators;
    }

    @Override
    public boolean test(IngestRecord record) {
        return validators.values().stream().allMatch(p -> p.test(record));
    }

    public ValidationResult validate(IngestRecord record, String filename) {
        List<String> errors = validators.entrySet().stream()
                .filter(e -> !e.getValue().test(record))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return errors.isEmpty() ? ValidationResult.ok(filename) : ValidationResult.failed(filename, errors);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, Predicate<IngestRecord>> validators = new LinkedHashMap<>();

        public Builder require(String name, Predicate<IngestRecord> predicate) {
            validators.put(name, predicate);
            return this;
        }

        public ValidationPipeline build() {
            return new ValidationPipeline(new LinkedHashMap<>(validators));
        }
    }

    public static ValidationPipeline standard() {
        return builder()
                .require("cgi-not-blank", r -> r.cgi() != null && !r.cgi().isBlank())
                .require("cgi-format", r -> r.cgi() != null && r.cgi().matches("\\d{3}-\\d{2}-\\d{4}-\\d{5}"))
                .require("timestamp-positive", r -> r.timestamp() > 0)
                .require("value-not-null", r -> r.value() != null)
                .build();
    }
}
