package com.example.csvprocessor.model;

/**
 * Integer or decimal range constraint declared in mapping.yml.
 *
 * <p>Example (mapping.yml):
 * <pre>{@code
 * validation:
 *   range:
 *     min: "1"
 *     max: "999"
 * }</pre>
 *
 * <p>Values are stored as strings so that SnakeYAML does not lose leading zeros
 * or precision; comparison is performed using {@link java.math.BigDecimal}.
 */
public class RangeRule {

    /** Inclusive lower bound. */
    private String min;

    /** Inclusive upper bound. */
    private String max;

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getMin() { return min; }
    public void setMin(String min) { this.min = min; }

    public String getMax() { return max; }
    public void setMax(String max) { this.max = max; }
}
