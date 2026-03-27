package com.example.csvprocessor.service;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.model.FieldMapping;
import com.example.csvprocessor.model.MappingConfiguration;
import com.example.csvprocessor.model.ProcessingResult;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core CSV processing engine.
 *
 * <p>For each input CSV file it:
 * <ol>
 *   <li>Streams rows one at a time (heap-friendly for arbitrarily large files)</li>
 *   <li>Maps source columns to target columns via {@code mapping.yml}</li>
 *   <li>Applies ordered transformations (TRIM, UPPERCASE, …)</li>
 *   <li>Validates each target value against the declared rules</li>
 *   <li>Writes valid rows to a {@code *_success_*.csv} file in the success dir</li>
 *   <li>Writes invalid rows (with an appended {@code QUARANTINE_REASON} column)
 *       to a {@code *_quarantine_*.csv} file in the quarantine dir</li>
 * </ol>
 *
 * <p>Both output files include the mapped header row.  Quarantine files are
 * deleted if no invalid rows were found (keeping the directory clean).
 *
 * <p>Progress is logged every 10 000 rows and output buffers are flushed every
 * 50 000 rows to limit memory use.
 */
@Service
public class CsvRowProcessor {

    private static final Logger log = LoggerFactory.getLogger(CsvRowProcessor.class);
    private static final int WRITE_BUFFER = 131_072; // 128 KB
    private static final int READ_BUFFER = 131_072;
    private static final int FLUSH_INTERVAL = 50_000;
    private static final int LOG_INTERVAL = 10_000;
    private static final String QUARANTINE_REASON_HEADER = "QUARANTINE_REASON";

    @Autowired
    private MappingConfigService mappingConfigService;

    @Autowired
    private DirectoryProperties directoryProperties;

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
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String baseName = stripExtension(csvFile.getName());

        File successFile = new File(directoryProperties.getOutputSuccess(),
                baseName + "_success_" + timestamp + ".csv");
        File quarantineFile = new File(directoryProperties.getOutputQuarantine(),
                baseName + "_quarantine_" + timestamp + ".csv");

        new File(directoryProperties.getOutputSuccess()).mkdirs();
        new File(directoryProperties.getOutputQuarantine()).mkdirs();

        long totalRows = 0, successCount = 0, quarantineCount = 0;

        CSVFormat inputFormat = buildInputFormat(config);

        try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(new FileInputStream(csvFile), config.getEncoding()),
                     READ_BUFFER);
             CSVParser parser = new CSVParser(reader, inputFormat);
             PrintWriter successWriter = openWriter(successFile, config.getEncoding());
             PrintWriter quarantineWriter = openWriter(quarantineFile, config.getEncoding())) {

            // Write output headers
            writeHeader(successWriter, config, false);
            writeHeader(quarantineWriter, config, true);

            // Skip extra header lines if configured
            long recordsToSkip = config.getSkipLines();

            for (CSVRecord record : parser) {
                if (recordsToSkip > 0) {
                    recordsToSkip--;
                    continue;
                }

                totalRows++;
                try {
                    RowResult result = processRow(record, config);

                    if (result.isValid()) {
                        successWriter.println(joinCsv(result.getValues(), config.getOutputDelimiter()));
                        successCount++;
                    } else {
                        String quarantineLine = joinCsv(result.getValues(), config.getOutputDelimiter())
                                + config.getOutputDelimiter()
                                + escapeCsvField(result.getErrorReason());
                        quarantineWriter.println(quarantineLine);
                        quarantineCount++;
                    }

                } catch (Exception e) {
                    log.warn("Unexpected error at row {} of '{}': {}", totalRows, csvFile.getName(), e.getMessage());
                    String rawRow = rawRecordString(record, config.getOutputDelimiter());
                    quarantineWriter.println(rawRow + config.getOutputDelimiter()
                            + escapeCsvField("PROCESSING_ERROR: " + e.getMessage()));
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

        // Clean up empty quarantine file (header only)
        if (quarantineCount == 0 && quarantineFile.exists()) {
            quarantineFile.delete();
        }
        // Clean up empty success file
        if (successCount == 0 && successFile.exists()) {
            successFile.delete();
        }

        log.info("Finished '{}': total={}, success={}, quarantine={}",
                csvFile.getName(), totalRows, successCount, quarantineCount);

        return new ProcessingResult(
                csvFile.getName(), totalRows, successCount, quarantineCount,
                successCount > 0 ? successFile : null,
                quarantineCount > 0 ? quarantineFile : null);
    }

    // -------------------------------------------------------------------------
    // Row processing
    // -------------------------------------------------------------------------

    private RowResult processRow(CSVRecord record, MappingConfiguration config) {
        List<String> outputValues = new ArrayList<>(config.getFields().size());
        List<String> errors = new ArrayList<>();

        for (FieldMapping field : config.getFields()) {
            String raw = extractField(record, field);
            String value = applyTransformations(raw, field.getTransformations());

            String error = validate(value, field);
            if (error != null) {
                errors.add(field.getTargetColumnName() + ": " + error);
            }

            // Resolve final output value
            String output;
            if (value == null || value.isEmpty()) {
                output = field.getDefaultValue() != null ? field.getDefaultValue() : "";
            } else {
                output = value;
            }
            outputValues.add(output);
        }

        if (errors.isEmpty()) {
            return RowResult.valid(outputValues);
        } else {
            return RowResult.invalid(outputValues, String.join(" | ", errors));
        }
    }

    // -------------------------------------------------------------------------
    // Field extraction
    // -------------------------------------------------------------------------

    private String extractField(CSVRecord record, FieldMapping field) {
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

    // -------------------------------------------------------------------------
    // Transformations
    // -------------------------------------------------------------------------

    private String applyTransformations(String value, List<String> transformations) {
        if (value == null || transformations == null || transformations.isEmpty()) {
            return value;
        }
        String result = value;
        for (String t : transformations) {
            switch (t.toUpperCase()) {
                case "TRIM":
                    result = result.trim();
                    break;
                case "UPPERCASE":
                    result = result.toUpperCase();
                    break;
                case "LOWERCASE":
                    result = result.toLowerCase();
                    break;
                case "REMOVE_SPACES":
                    result = result.replaceAll("\\s+", "");
                    break;
                case "NUMERIC_ONLY":
                    result = result.replaceAll("[^0-9.]", "");
                    break;
                case "REMOVE_NON_PRINTABLE":
                    result = result.replaceAll("[^\\x20-\\x7E]", "");
                    break;
                default:
                    log.warn("Unknown transformation '{}' — skipped", t);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /**
     * Returns a human-readable error message, or {@code null} if valid.
     */
    private String validate(String value, FieldMapping field) {
        boolean blank = value == null || value.isEmpty();

        if (field.isRequired() && blank) {
            return "required field is empty";
        }
        if (blank) {
            return null; // optional, skip further checks
        }

        ValidationRule rule = field.getValidation();
        if (rule == null) return null;

        if (rule.getPattern() != null && !value.matches(rule.getPattern())) {
            return "value '" + value + "' does not match pattern " + rule.getPattern();
        }

        if (rule.getMinLength() != null && value.length() < rule.getMinLength()) {
            return "length " + value.length() + " is below minimum " + rule.getMinLength();
        }
        if (rule.getMaxLength() != null && value.length() > rule.getMaxLength()) {
            return "length " + value.length() + " exceeds maximum " + rule.getMaxLength();
        }

        if (rule.getMinValue() != null || rule.getMaxValue() != null) {
            try {
                BigDecimal num = new BigDecimal(value);
                if (rule.getMinValue() != null
                        && num.compareTo(new BigDecimal(rule.getMinValue())) < 0) {
                    return "value " + value + " is below minimum " + rule.getMinValue();
                }
                if (rule.getMaxValue() != null
                        && num.compareTo(new BigDecimal(rule.getMaxValue())) > 0) {
                    return "value " + value + " exceeds maximum " + rule.getMaxValue();
                }
            } catch (NumberFormatException e) {
                return "value '" + value + "' is not a valid number";
            }
        }

        if (rule.getAllowedValues() != null && !rule.getAllowedValues().isEmpty()
                && !rule.getAllowedValues().contains(value)) {
            return "value '" + value + "' is not in allowed list " + rule.getAllowedValues();
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Output helpers
    // -------------------------------------------------------------------------

    private CSVFormat buildInputFormat(MappingConfiguration config) {
        CSVFormat fmt = CSVFormat.DEFAULT
                .withDelimiter(config.getInputDelimiter().charAt(0))
                .withIgnoreEmptyLines(true)
                .withTrim(false);  // transformations handle trimming explicitly
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

    private void writeHeader(PrintWriter writer, MappingConfiguration config, boolean includeErrorCol) {
        List<String> headers = config.getFields().stream()
                .map(FieldMapping::getTargetColumnName)
                .collect(Collectors.toList());
        if (includeErrorCol) {
            headers.add(QUARANTINE_REASON_HEADER);
        }
        writer.println(String.join(config.getOutputDelimiter(), headers));
    }

    private String joinCsv(List<String> values, String delimiter) {
        return values.stream()
                .map(this::escapeCsvField)
                .collect(Collectors.joining(delimiter));
    }

    private String escapeCsvField(String value) {
        if (value == null) return "";
        // Wrap in quotes if the value contains the delimiter, quotes, or newlines
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String rawRecordString(CSVRecord record, String delimiter) {
        List<String> cols = new ArrayList<>();
        for (String val : record) cols.add(escapeCsvField(val));
        return String.join(delimiter, cols);
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
            this.values = values;
            this.valid = valid;
            this.errorReason = errorReason;
        }

        static RowResult valid(List<String> values) {
            return new RowResult(values, true, null);
        }

        static RowResult invalid(List<String> values, String error) {
            return new RowResult(values, false, error);
        }

        boolean isValid() { return valid; }
        List<String> getValues() { return values; }
        String getErrorReason() { return errorReason; }
    }
}
