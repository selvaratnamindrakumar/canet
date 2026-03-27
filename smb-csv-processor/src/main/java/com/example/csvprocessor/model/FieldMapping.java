package com.example.csvprocessor.model;

import java.util.Collections;
import java.util.List;

/**
 * Describes how a single input column maps to a target output column.
 *
 * <p>In mapping.yml a field looks like:
 * <pre>{@code
 * fields:
 *   - sourceColumnIndex: 0         # zero-based; used when hasHeader=false
 *     sourceColumnName: "cust_id"  # used when hasHeader=true (takes precedence)
 *     targetColumnName: "CUSTOMER_ID"
 *     type: STRING
 *     required: true
 *     transformations:
 *       - TRIM
 *       - UPPERCASE
 *     validation:
 *       pattern: "[A-Z0-9]{6,12}"
 * }</pre>
 */
public class FieldMapping {

    /**
     * Zero-based column index in the source CSV.  Used when {@code sourceColumnName}
     * is blank or when the file has no header row.
     */
    private int sourceColumnIndex = -1;

    /**
     * Header name in the source CSV.  When {@code hasHeader=true} and this value
     * is non-blank, it takes precedence over {@code sourceColumnIndex}.
     */
    private String sourceColumnName;

    /** Column name written to the output (success / quarantine) file header. */
    private String targetColumnName;

    /**
     * Logical data type used during validation.
     * Supported: {@code STRING}, {@code INTEGER}, {@code DECIMAL}, {@code DATE}, {@code BOOLEAN}.
     */
    private String type = "STRING";

    /** Whether an empty or missing value is treated as a validation error. */
    private boolean required = false;

    /**
     * Value written to the output when the source field is empty/missing and the
     * field is not required.  {@code null} leaves the output field blank.
     */
    private String defaultValue;

    /**
     * Ordered list of transformations applied before validation.
     * Supported tokens (case-insensitive):
     * <ul>
     *   <li>{@code TRIM} — strip leading/trailing whitespace</li>
     *   <li>{@code UPPERCASE} — convert to upper case</li>
     *   <li>{@code LOWERCASE} — convert to lower case</li>
     *   <li>{@code REMOVE_SPACES} — delete all whitespace characters</li>
     *   <li>{@code NUMERIC_ONLY} — keep only digits and {@code .}</li>
     *   <li>{@code REMOVE_NON_PRINTABLE} — strip non-printable ASCII characters</li>
     * </ul>
     */
    private List<String> transformations = Collections.emptyList();

    /** Optional validation constraints. */
    private ValidationRule validation;

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public int getSourceColumnIndex() { return sourceColumnIndex; }
    public void setSourceColumnIndex(int sourceColumnIndex) { this.sourceColumnIndex = sourceColumnIndex; }

    public String getSourceColumnName() { return sourceColumnName; }
    public void setSourceColumnName(String sourceColumnName) { this.sourceColumnName = sourceColumnName; }

    public String getTargetColumnName() { return targetColumnName; }
    public void setTargetColumnName(String targetColumnName) { this.targetColumnName = targetColumnName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public List<String> getTransformations() { return transformations; }
    public void setTransformations(List<String> transformations) {
        this.transformations = transformations != null ? transformations : Collections.emptyList();
    }

    public ValidationRule getValidation() { return validation; }
    public void setValidation(ValidationRule validation) { this.validation = validation; }
}
