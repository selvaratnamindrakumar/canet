package com.canet.fileproc.model;

import java.util.List;

public record ValidationResult(
        boolean valid,
        List<String> errors,
        String filename
) {
    public static ValidationResult ok(String filename) {
        return new ValidationResult(true, List.of(), filename);
    }

    public static ValidationResult failed(String filename, List<String> errors) {
        return new ValidationResult(false, errors, filename);
    }
}
