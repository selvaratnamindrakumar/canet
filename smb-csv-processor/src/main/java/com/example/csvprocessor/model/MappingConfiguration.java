package com.example.csvprocessor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Root object deserialised from {@code mapping.yml}.
 *
 * <p>SnakeYAML maps the top-level key {@code mapping} onto this class.
 */
public class MappingConfiguration {

    /** Character used to split columns in the input CSV (default comma). */
    private String inputDelimiter = ",";

    /** Character used to join columns in the output CSV (default comma). */
    private String outputDelimiter = ",";

    /**
     * Whether the first data row is a header.  When {@code true}, column names
     * can be used via {@link FieldMapping#getSourceColumnName()}.
     */
    private boolean hasHeader = true;

    /** File encoding of both input and output files. */
    private String encoding = "UTF-8";

    /**
     * Number of non-header rows to skip at the beginning of the file
     * (e.g. secondary headers or comment lines).
     */
    private int skipLines = 0;

    /** Ordered list of field mappings. */
    private List<FieldMapping> fields = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getInputDelimiter() { return inputDelimiter; }
    public void setInputDelimiter(String inputDelimiter) { this.inputDelimiter = inputDelimiter; }

    public String getOutputDelimiter() { return outputDelimiter; }
    public void setOutputDelimiter(String outputDelimiter) { this.outputDelimiter = outputDelimiter; }

    public boolean isHasHeader() { return hasHeader; }
    public void setHasHeader(boolean hasHeader) { this.hasHeader = hasHeader; }

    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }

    public int getSkipLines() { return skipLines; }
    public void setSkipLines(int skipLines) { this.skipLines = skipLines; }

    public List<FieldMapping> getFields() { return fields; }
    public void setFields(List<FieldMapping> fields) {
        this.fields = fields != null ? fields : new ArrayList<>();
    }
}
