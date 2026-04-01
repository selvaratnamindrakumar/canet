package com.example.csvprocessor.service;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.config.OutputProperties;
import com.example.csvprocessor.model.FieldMapping;
import com.example.csvprocessor.model.InputFieldRef;
import com.example.csvprocessor.model.MappingConfiguration;
import com.example.csvprocessor.model.ProcessingResult;
import com.example.csvprocessor.model.RangeRule;
import com.example.csvprocessor.model.ValidationRule;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core CSV processing engine.
 *
 * <p>For each input CSV file it:
 * <ol>
 *   <li>Streams rows one at a time (64 KB read buffer — heap-friendly for arbitrarily large files)</li>
 *   <li>Builds a full row context map (column name → value) for formula evaluation</li>
 *   <li>Maps source columns to target columns via {@code mapping.yml} — direct or formula-derived</li>
 *   <li>Applies ordered transformations (TRIM, UPPERCASE, …)</li>
 *   <li>Validates each target value: nullable, expectedType, range, pattern, allowedValues</li>
 *   <li>Writes valid rows to a {@code *_success_*.csv} in IE/AA quoted-uppercase format when configured</li>
 *   <li>Writes invalid rows (with a {@code QUARANTINE_REASON} column) to the quarantine directory</li>
 * </ol>
 *
 * <p>IE/AA export format wraps every header and value in double quotes:
 * {@code "MCC","MNC","LAC",...}
 *
 * <p>Progress is logged every 10 000 rows; output buffers are flushed every 50 000 rows.
 */
@Service
public class CsvRowProcessor {

    private static final Logger log = LoggerFactory.getLogger(CsvRowProcessor.class);

    private static final int WRITE_BUFFER   = 131_072; // 128 KB
    private static final int READ_BUFFER    = 65_536;  //  64 KB
    private static final int FLUSH_INTERVAL = 50_000;
    private static final int LOG_INTERVAL   = 10_000;
    private static final String QUARANTINE_REASON_HEADER = "QUARANTINE_REASON";

    @Autowired
    private MappingConfigService mappingConfigService;

    @Autowired
    private DirectoryProperties directoryProperties;

    @Autowired
    private FormulaService formulaService;

    @Autowired
    private OutputProperties outputProperties;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Processes a single CSV file end-to-end.
     *
     * @param csvFile the extracted CSV to process
     * @return summary with row counts and output file references
     * @throws IOException on I/O errors
     */
    public ProcessingResult processFile(File csvFile) throws IOException {
        MappingConfiguration config = mappingConfigService.getConfiguration();
        String timestamp = new SimpleDateFormat(outputProperties.getTimestampFormat()).format(new Date());
        String baseName  = stripExtension(csvFile.getName());

        File successFile    = new File(directoryProperties.getOutputSuccess(),
                baseName + "_" + outputProperties.getSuccessPrefix() + "_" + timestamp + ".csv");
        File quarantineFile = new File(directoryProperties.getOutputQuarantine(),
                baseName + "_" + outputProperties.getQuarantinePrefix() + "_" + timestamp + ".csv");

        new File(directoryProperties.getOutputSuccess()).mkdirs();
        new File(directoryProperties.getOutputQuarantine()).mkdirs();

        long totalRows = 0, successCount = 0, quarantineCount = 0;

        CSVFormat inputFormat = buildInputFormat(config);

        try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(new FileInputStream(csvFile), config.getEncoding()),
                     READ_BUFFER);
             CSVParser parser = new CSVParser(reader, inputFormat);
             PrintWriter successWriter    = openWriter(successFile,    config.getEncoding());
             PrintWriter quarantineWriter = openWriter(quarantineFile, config.getEncoding())) {

            writeHeader(successWriter,    config, false);
            writeHeader(quarantineWriter, config, true);

            long recordsToSkip = config.getSkipLines();

            for (CSVRecord record : parser) {
                if (recordsToSkip > 0) { recordsToSkip--; continue; }

                totalRows++;
                try {
                    // Build full context map (lower-cased key → raw value) for formula use
                    Map<String, String> rowContext = buildRowContext(record, config);

                    RowResult result = processRow(record, rowContext, config);

                    if (result.isValid()) {
                        successWriter.println(formatRow(result.getValues(), config, false));
                        successCount++;
                    } else {
                        String quarantineLine = formatRow(result.getValues(), config, false)
                                + config.getOutputDelimiter()
                                + quoteField(result.getErrorReason(), config);
                        quarantineWriter.println(quarantineLine);
                        quarantineCount++;
                    }
                } catch (Exception e) {
                    log.warn("Unexpected error at row {} of '{}': {}",
                            totalRows, csvFile.getName(), e.getMessage());
                    String rawRow = rawRecordString(record, config);
                    quarantineWriter.println(rawRow + config.getOutputDelimiter()
                            + quoteField("PROCESSING_ERROR: " + e.getMessage(), config));
                    quarantineCount++;
                }

                if (totalRows % LOG_INTERVAL == 0) {
                    log.info("Progress '{}': {} rows processed (success={}, quarantine={})",
                            csvFile.getName(), totalRows, successCount, quarantineCount);
                }
                if (totalRows % FLUSH_INTERVAL == 0) {
                    successWriter.flush();
                    quarantineWriter.flush();
                }
            }
        }

        if (quarantineCount == 0 && quarantineFile.exists()) quarantineFile.delete();
        if (successCount    == 0 && successFile.exists())    successFile.delete();

        log.info("Finished '{}': total={}, success={}, quarantine={}",
                csvFile.getName(), totalRows, successCount, quarantineCount);

        return new ProcessingResult(
                csvFile.getName(), totalRows, successCount, quarantineCount,
                successCount    > 0 ? successFile    : null,
                quarantineCount > 0 ? quarantineFile : null);
    }

    // -------------------------------------------------------------------------
    // Row context map
    // -------------------------------------------------------------------------

    /**
     * Builds a lower-cased-key map of every column value in the record.
     * Used by formula evaluations that need cross-field access (e.g. calculateTac
     * reading both generation and 5g_tac in the same row).
     */
    private Map<String, String> buildRowContext(CSVRecord record, MappingConfiguration config) {
        Map<String, String> ctx = new HashMap<>();
        if (config.isHasHeader()) {
            Map<String, Integer> headerMap = record.getParser().getHeaderMap();
            if (headerMap != null) {
                for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                    int idx = entry.getValue();
                    if (idx < record.size()) {
                        ctx.put(entry.getKey().toLowerCase().trim(), record.get(idx));
                    }
                }
            }
        } else {
            for (int i = 0; i < record.size(); i++) {
                ctx.put(String.valueOf(i), record.get(i));
            }
        }
        return ctx;
    }

    // -------------------------------------------------------------------------
    // Row processing
    // -------------------------------------------------------------------------

    private RowResult processRow(CSVRecord record, Map<String, String> rowContext,
                                  MappingConfiguration config) {
        List<String> outputValues = new ArrayList<>(config.getFields().size());
        List<String> errors       = new ArrayList<>();

        for (FieldMapping field : config.getFields()) {
            String value;

            if (field.isFormulaField()) {
                // Multi-input / formula field
                value = formulaService.evaluate(field.getFormula(), rowContext);
                if (value != null) {
                    value = applyTransformations(value, field.getTransformations());
                }
            } else {
                // Direct 1:1 field — try inputFields list first, then sourceColumnName/Index
                String raw = extractField(record, field);
                value = applyTransformations(raw, field.getTransformations());
            }

            // Nullable / required check
            boolean blank = value == null || value.isEmpty();
            if (blank && !field.isEffectivelyNullable()) {
                errors.add(field.getTargetColumnName() + ": required field is empty");
                outputValues.add("");
                continue;
            }

            // Type + constraint validation (skip when blank and nullable)
            if (!blank) {
                String error = validate(value, field);
                if (error != null) {
                    errors.add(field.getTargetColumnName() + ": " + error);
                }
            }

            // Final output value
            String output = blank
                    ? (field.getDefaultValue() != null ? field.getDefaultValue() : "")
                    : value;
            outputValues.add(output);
        }

        return errors.isEmpty()
                ? RowResult.valid(outputValues)
                : RowResult.invalid(outputValues, String.join(" | ", errors));
    }

    // -------------------------------------------------------------------------
    // Field extraction
    // -------------------------------------------------------------------------

    private String extractField(CSVRecord record, FieldMapping field) {
        // Try inputFields list first (single-element direct reference)
        if (field.getInputFields() != null && !field.getInputFields().isEmpty()) {
            InputFieldRef ref = field.getInputFields().get(0);
            return extractByRef(record, ref);
        }
        // Fall back to sourceColumnName / sourceColumnIndex
        try {
            String name = field.getSourceColumnName();
            if (name != null && !name.isBlank() && record.isMapped(name)) {
                return record.get(name);
            }
            int idx = field.getSourceColumnIndex();
            if (idx >= 0 && idx < record.size()) {
                return record.get(idx);
            }
        } catch (Exception e) {
            log.trace("Could not extract field '{}': {}", field.getSourceColumnName(), e.getMessage());
        }
        return null;
    }

    private String extractByRef(CSVRecord record, InputFieldRef ref) {
        try {
            String name = ref.getName();
            if (name != null && !name.isBlank() && record.isMapped(name)) {
                return record.get(name);
            }
            int idx = ref.getIndex();
            if (idx >= 0 && idx < record.size()) {
                return record.get(idx);
            }
        } catch (Exception e) {
            log.trace("Could not extract ref '{}': {}", ref.getName(), e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Transformations
    // -------------------------------------------------------------------------

    private String applyTransformations(String value, List<String> transformations) {
        if (value == null || transformations == null || transformations.isEmpty()) return value;
        String result = value;
        for (String t : transformations) {
            switch (t.toUpperCase()) {
                case "TRIM":                 result = result.trim(); break;
                case "UPPERCASE":            result = result.toUpperCase(); break;
                case "LOWERCASE":            result = result.toLowerCase(); break;
                case "REMOVE_SPACES":        result = result.replaceAll("\\s+", ""); break;
                case "NUMERIC_ONLY":         result = result.replaceAll("[^0-9.]", ""); break;
                case "REMOVE_NON_PRINTABLE": result = result.replaceAll("[^\\x20-\\x7E]", ""); break;
                default: log.warn("Unknown transformation '{}' — skipped", t);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private String validate(String value, FieldMapping field) {
        // Type check
        String type = field.getExpectedType() != null
                ? field.getExpectedType().toUpperCase()
                : "STRING";

        if ("INTEGER".equals(type)) {
            try { Long.parseLong(value.trim()); }
            catch (NumberFormatException e) {
                return "value '" + value + "' is not a valid INTEGER";
            }
        } else if ("DECIMAL".equals(type)) {
            try { new BigDecimal(value.trim()); }
            catch (NumberFormatException e) {
                return "value '" + value + "' is not a valid DECIMAL";
            }
        } else if ("BOOLEAN".equals(type)) {
            String v = value.trim().toLowerCase();
            if (!"true".equals(v) && !"false".equals(v) && !"1".equals(v) && !"0".equals(v)) {
                return "value '" + value + "' is not a valid BOOLEAN";
            }
        }

        ValidationRule rule = field.getValidation();
        if (rule == null) return null;

        if (rule.getPattern() != null && !value.matches(rule.getPattern())) {
            return "value '" + value + "' does not match pattern " + rule.getPattern();
        }

        if (rule.getMinLength() != null && value.length() < rule.getMinLength()) {
            return "length " + value.length() + " below minimum " + rule.getMinLength();
        }
        if (rule.getMaxLength() != null && value.length() > rule.getMaxLength()) {
            return "length " + value.length() + " exceeds maximum " + rule.getMaxLength();
        }

        // Range validation (preferred) — also covers legacy minValue/maxValue
        RangeRule range = rule.getRange();
        String minVal = (range != null && range.getMin() != null) ? range.getMin() : rule.getMinValue();
        String maxVal = (range != null && range.getMax() != null) ? range.getMax() : rule.getMaxValue();

        if (minVal != null || maxVal != null) {
            try {
                BigDecimal num = new BigDecimal(value.trim());
                if (minVal != null && num.compareTo(new BigDecimal(minVal)) < 0) {
                    return "value " + value + " below minimum " + minVal;
                }
                if (maxVal != null && num.compareTo(new BigDecimal(maxVal)) > 0) {
                    return "value " + value + " exceeds maximum " + maxVal;
                }
            } catch (NumberFormatException e) {
                return "value '" + value + "' is not numeric (required for range check)";
            }
        }

        if (rule.getAllowedValues() != null && !rule.getAllowedValues().isEmpty()
                && !rule.getAllowedValues().contains(value)) {
            return "value '" + value + "' not in allowed list " + rule.getAllowedValues();
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Output helpers — IE/AA quoted-uppercase format
    // -------------------------------------------------------------------------

    private CSVFormat buildInputFormat(MappingConfiguration config) {
        CSVFormat fmt = CSVFormat.DEFAULT
                .withDelimiter(config.getInputDelimiter().charAt(0))
                .withIgnoreEmptyLines(true)
                .withTrim(false);
        if (config.isHasHeader()) {
            fmt = fmt.withFirstRecordAsHeader().withIgnoreHeaderCase(true);
        }
        return fmt;
    }

    private PrintWriter openWriter(File file, String encoding) throws IOException {
        return new PrintWriter(
                new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(file), encoding),
                        WRITE_BUFFER));
    }

    private void writeHeader(PrintWriter writer, MappingConfiguration config,
                              boolean includeErrorCol) {
        List<String> headers = config.getFields().stream()
                .map(FieldMapping::getTargetColumnName)
                .collect(Collectors.toList());
        if (includeErrorCol) headers.add(QUARANTINE_REASON_HEADER);

        if (config.isQuotedUppercaseOutput()) {
            String line = headers.stream()
                    .map(h -> "\"" + h.toUpperCase().replace("\"", "\"\"") + "\"")
                    .collect(Collectors.joining(config.getOutputDelimiter()));
            writer.println(line);
        } else {
            writer.println(String.join(config.getOutputDelimiter(), headers));
        }
    }

    /**
     * Formats a row of values as a CSV line.
     *
     * <p>When {@code outputFormat=QUOTED_UPPERCASE} every field is wrapped in double
     * quotes (IE/AA export spec): {@code "MCC","MNC","LAC",...}
     */
    private String formatRow(List<String> values, MappingConfiguration config,
                              boolean isQuarantineReason) {
        if (config.isQuotedUppercaseOutput()) {
            return values.stream()
                    .map(v -> "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"")
                    .collect(Collectors.joining(config.getOutputDelimiter()));
        }
        return values.stream()
                .map(this::escapeCsvField)
                .collect(Collectors.joining(config.getOutputDelimiter()));
    }

    private String quoteField(String value, MappingConfiguration config) {
        if (config.isQuotedUppercaseOutput()) {
            return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
        }
        return escapeCsvField(value);
    }

    private String escapeCsvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String rawRecordString(CSVRecord record, MappingConfiguration config) {
        List<String> cols = new ArrayList<>();
        for (String val : record) {
            cols.add(config.isQuotedUppercaseOutput()
                    ? "\"" + val.replace("\"", "\"\"") + "\""
                    : escapeCsvField(val));
        }
        return String.join(config.getOutputDelimiter(), cols);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // -------------------------------------------------------------------------
    // Internal row result
    // -------------------------------------------------------------------------

    private static final class RowResult {
        private final List<String> values;
        private final boolean valid;
        private final String errorReason;

        private RowResult(List<String> values, boolean valid, String errorReason) {
            this.values      = values;
            this.valid       = valid;
            this.errorReason = errorReason;
        }

        static RowResult valid(List<String> values) { return new RowResult(values, true, null); }
        static RowResult invalid(List<String> values, String err) { return new RowResult(values, false, err); }

        boolean isValid()           { return valid; }
        List<String> getValues()    { return values; }
        String getErrorReason()     { return errorReason; }
    }
}
