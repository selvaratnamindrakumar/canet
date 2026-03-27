package com.example.csvprocessor.model;

/**
 * Reference to one source input column used by a formula or multi-input mapping.
 *
 * <p>In mapping.yml a multi-input field looks like:
 * <pre>{@code
 * inputFields:
 *   - name: "4g-tac"
 *   - name: "5g_tac"
 * formula: calculateTac
 * }</pre>
 */
public class InputFieldRef {

    /**
     * Header name of the source column (used when hasHeader=true).
     */
    private String name;

    /**
     * Zero-based column index (fallback when name is blank or hasHeader=false).
     */
    private int index = -1;

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
}
