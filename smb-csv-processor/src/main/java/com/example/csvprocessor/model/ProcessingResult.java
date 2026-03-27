package com.example.csvprocessor.model;

import java.io.File;

/**
 * Summary returned by {@link com.example.csvprocessor.service.CsvRowProcessor}
 * after processing a single CSV file.
 */
public class ProcessingResult {

    private final String sourceFileName;
    private final long totalRows;
    private final long successCount;
    private final long quarantineCount;

    /** Non-null only when at least one row was valid. */
    private final File successFile;

    /** Non-null only when at least one row was invalid. */
    private final File quarantineFile;

    public ProcessingResult(String sourceFileName, long totalRows,
                            long successCount, long quarantineCount,
                            File successFile, File quarantineFile) {
        this.sourceFileName = sourceFileName;
        this.totalRows = totalRows;
        this.successCount = successCount;
        this.quarantineCount = quarantineCount;
        this.successFile = successFile;
        this.quarantineFile = quarantineFile;
    }

    public String getSourceFileName() { return sourceFileName; }
    public long getTotalRows() { return totalRows; }
    public long getSuccessCount() { return successCount; }
    public long getQuarantineCount() { return quarantineCount; }
    public File getSuccessFile() { return successFile; }
    public File getQuarantineFile() { return quarantineFile; }

    public boolean hasSuccessFile() { return successFile != null; }
    public boolean hasQuarantineFile() { return quarantineFile != null; }

    @Override
    public String toString() {
        return String.format("ProcessingResult{source='%s', total=%d, success=%d, quarantine=%d}",
                sourceFileName, totalRows, successCount, quarantineCount);
    }
}
