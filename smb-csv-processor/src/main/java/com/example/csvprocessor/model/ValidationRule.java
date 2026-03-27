package com.example.csvprocessor.model;

import java.util.List;

/**
 * Validation constraints for a single mapped field, as declared in mapping.yml.
 */
public class ValidationRule {

    /**
     * Java regex the field value must fully match ({@link String#matches(String)}).
     * Example: {@code "[A-Z0-9]{6,12}"}
     */
    private String pattern;

    /** Minimum numeric value (inclusive). Compared as {@link java.math.BigDecimal}. */
    private String minValue;

    /** Maximum numeric value (inclusive). */
    private String maxValue;

    /** Minimum string length (inclusive). */
    private Integer minLength;

    /** Maximum string length (inclusive). */
    private Integer maxLength;

    /**
     * If set, the value (after transformations) must be one of these strings.
     * Comparison is case-sensitive unless a UPPERCASE/LOWERCASE transformation is applied first.
     */
    private List<String> allowedValues;

    /**
     * Numeric range constraint (inclusive on both bounds).
     * Provides a more readable alternative to {@code minValue} + {@code maxValue}.
     *
     * <p>Example:
     * <pre>{@code
     * validation:
     *   range:
     *     min: "1"
     *     max: "999"
     * }</pre>
     */
    private RangeRule range;

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getMinValue() { return minValue; }
    public void setMinValue(String minValue) { this.minValue = minValue; }

    public String getMaxValue() { return maxValue; }
    public void setMaxValue(String maxValue) { this.maxValue = maxValue; }

    public Integer getMinLength() { return minLength; }
    public void setMinLength(Integer minLength) { this.minLength = minLength; }

    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

    public List<String> getAllowedValues() { return allowedValues; }
    public void setAllowedValues(List<String> allowedValues) { this.allowedValues = allowedValues; }

    public RangeRule getRange() { return range; }
    public void setRange(RangeRule range) { this.range = range; }
}
