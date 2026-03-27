package com.example.csvprocessor.model;

import java.util.Collections;
import java.util.List;

/**
 * Describes how one output column is produced from the source CSV.
 *
 * <p>A field may be:
 * <ul>
 *   <li><b>Direct</b> — one source column copied (and transformed) to one output column.
 *       Use {@code sourceColumnName} / {@code sourceColumnIndex}.</li>
 *   <li><b>Multi-input</b> — multiple source columns fed into a {@code formula}.
 *       Use {@code inputFields} + {@code formula}.</li>
 * </ul>
 *
 * <p>Typical mapping.yml entry (direct):
 * <pre>{@code
 * - sourceColumnName: "mcc"
 *   targetColumnName: "MCC"
 *   expectedType: INTEGER
 *   nullable: false
 *   validation:
 *     range:
 *       min: "1"
 *       max: "999"
 * }</pre>
 *
 * <p>Typical mapping.yml entry (formula / multi-input):
 * <pre>{@code
 * - targetColumnName: "TAC"
 *   inputFields:
 *     - name: "4g-tac"
 *     - name: "5g_tac"
 *   formula: calculateTac
 *   expectedType: INTEGER
 *   nullable: true
 * }</pre>
 */
public class FieldMapping {

    /**
     * Zero-based column index in the source CSV.
     * Used when {@code sourceColumnName} is blank or hasHeader=false.
     */
    private int sourceColumnIndex = -1;

    /**
     * Header name in the source CSV.
     * When {@code hasHeader=true} and non-blank, takes precedence over {@code sourceColumnIndex}.
     */
    private String sourceColumnName;

    /** Column name written to the output file header. */
    private String targetColumnName;

    /**
     * Multiple source columns that feed this output field.
     * When present, a {@code formula} must also be specified.
     */
    private List<InputFieldRef> inputFields = Collections.emptyList();

    /**
     * Named formula applied to produce the output value.
     * Supported: {@code calculateTac}, {@code deriveEci}, {@code deriveNodebId},
     * {@code deriveCvMbx}, {@code derivePci}, {@code deriveLatitude}, {@code deriveLongitude}.
     */
    private String formula;

    /**
     * Expected data type — used for type-aware validation.
     * Supported: {@code STRING}, {@code INTEGER}, {@code DECIMAL}, {@code BOOLEAN}.
     */
    private String expectedType = "STRING";

    /**
     * Whether the field may be empty / absent.
     * When {@code false} an empty value is a validation error.
     * (Replaces the older {@code required} flag but both are supported for backward compatibility.)
     */
    private boolean nullable = true;

    /**
     * Backward-compatible alias: {@code required: true} is equivalent to {@code nullable: false}.
     * Takes effect only when {@code nullable} is not explicitly set in the YAML.
     */
    private boolean required = false;

    /**
     * Value written when the source field is empty and {@code nullable=true}.
     * {@code null} leaves the output field blank.
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
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if this field has a formula and one or more input field references.
     */
    public boolean isFormulaField() {
        return formula != null && !formula.isBlank()
                && inputFields != null && !inputFields.isEmpty();
    }

    /**
     * Effective nullability: a field is nullable unless explicitly {@code nullable=false}
     * or the legacy {@code required=true} flag is set.
     */
    public boolean isEffectivelyNullable() {
        return nullable && !required;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public int getSourceColumnIndex() { return sourceColumnIndex; }
    public void setSourceColumnIndex(int sourceColumnIndex) { this.sourceColumnIndex = sourceColumnIndex; }

    public String getSourceColumnName() { return sourceColumnName; }
    public void setSourceColumnName(String sourceColumnName) { this.sourceColumnName = sourceColumnName; }

    public String getTargetColumnName() { return targetColumnName; }
    public void setTargetColumnName(String targetColumnName) { this.targetColumnName = targetColumnName; }

    public List<InputFieldRef> getInputFields() { return inputFields; }
    public void setInputFields(List<InputFieldRef> inputFields) {
        this.inputFields = inputFields != null ? inputFields : Collections.emptyList();
    }

    public String getFormula() { return formula; }
    public void setFormula(String formula) { this.formula = formula; }

    public String getExpectedType() { return expectedType; }
    public void setExpectedType(String expectedType) { this.expectedType = expectedType; }

    public boolean isNullable() { return nullable; }
    public void setNullable(boolean nullable) { this.nullable = nullable; }

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
