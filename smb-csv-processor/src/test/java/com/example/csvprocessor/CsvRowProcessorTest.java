package com.example.csvprocessor;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.model.FieldMapping;
import com.example.csvprocessor.model.MappingConfiguration;
import com.example.csvprocessor.model.ProcessingResult;
import com.example.csvprocessor.model.ValidationRule;
import com.example.csvprocessor.service.CsvRowProcessor;
import com.example.csvprocessor.service.MappingConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CSV row processor — no Spring context needed.
 */
class CsvRowProcessorTest {

    @TempDir
    Path tempDir;

    private CsvRowProcessor processor;
    private MappingConfigService mockMappingService;
    private DirectoryProperties dirs;

    @BeforeEach
    void setUp() {
        processor = new CsvRowProcessor();

        mockMappingService = Mockito.mock(MappingConfigService.class);
        ReflectionTestUtils.setField(processor, "mappingConfigService", mockMappingService);

        dirs = new DirectoryProperties();
        ReflectionTestUtils.setField(dirs, "outputSuccess", tempDir.resolve("success").toString());
        ReflectionTestUtils.setField(dirs, "outputQuarantine", tempDir.resolve("quarantine").toString());
        ReflectionTestUtils.setField(processor, "directoryProperties", dirs);

        // Ensure output dirs exist
        tempDir.resolve("success").toFile().mkdirs();
        tempDir.resolve("quarantine").toFile().mkdirs();
    }

    @Test
    void validRows_goToSuccessFile() throws Exception {
        Mockito.when(mockMappingService.getConfiguration()).thenReturn(buildConfig());

        File csv = writeCsv("id,name,amount\n"
                + "ABC001,John Smith,100.00\n"
                + "DEF002,Jane Doe,250.50\n");

        ProcessingResult result = processor.processFile(csv);

        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getQuarantineCount());
        assertNotNull(result.getSuccessFile());
        assertNull(result.getQuarantineFile());
    }

    @Test
    void invalidRows_goToQuarantineFile() throws Exception {
        Mockito.when(mockMappingService.getConfiguration()).thenReturn(buildConfig());

        File csv = writeCsv("id,name,amount\n"
                + "ABC001,John Smith,100.00\n"   // valid
                + ",Missing ID,50.00\n"           // invalid — required id empty
                + "BAD!,Good Name,-1.00\n");       // invalid — bad id and negative amount

        ProcessingResult result = processor.processFile(csv);

        assertEquals(3, result.getTotalRows());
        assertEquals(1, result.getSuccessCount());
        assertEquals(2, result.getQuarantineCount());
        assertNotNull(result.getSuccessFile());
        assertNotNull(result.getQuarantineFile());
    }

    @Test
    void allRowsValid_noQuarantineFileCreated() throws Exception {
        Mockito.when(mockMappingService.getConfiguration()).thenReturn(buildConfig());

        File csv = writeCsv("id,name,amount\n"
                + "XYZ999,Alice,75.25\n");

        ProcessingResult result = processor.processFile(csv);

        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getQuarantineCount());
        assertFalse(tempDir.resolve("quarantine").toFile().listFiles() != null
                && tempDir.resolve("quarantine").toFile().listFiles().length > 0,
                "Quarantine dir should be empty when all rows are valid");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private File writeCsv(String content) throws Exception {
        File f = tempDir.resolve("test_input.csv").toFile();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.print(content);
        }
        return f;
    }

    private MappingConfiguration buildConfig() {
        MappingConfiguration cfg = new MappingConfiguration();
        cfg.setHasHeader(true);
        cfg.setInputDelimiter(",");
        cfg.setOutputDelimiter(",");
        cfg.setEncoding("UTF-8");

        FieldMapping id = new FieldMapping();
        id.setSourceColumnName("id");
        id.setTargetColumnName("CUSTOMER_ID");
        id.setRequired(true);
        id.setTransformations(Arrays.asList("TRIM", "UPPERCASE"));
        ValidationRule idRule = new ValidationRule();
        idRule.setPattern("[A-Z0-9]{3,10}");
        id.setValidation(idRule);

        FieldMapping name = new FieldMapping();
        name.setSourceColumnName("name");
        name.setTargetColumnName("FULL_NAME");
        name.setRequired(true);
        name.setTransformations(Collections.singletonList("TRIM"));

        FieldMapping amount = new FieldMapping();
        amount.setSourceColumnName("amount");
        amount.setTargetColumnName("AMOUNT");
        amount.setRequired(true);
        amount.setTransformations(Collections.singletonList("TRIM"));
        ValidationRule amtRule = new ValidationRule();
        amtRule.setMinValue("0.00");
        amount.setValidation(amtRule);

        cfg.setFields(Arrays.asList(id, name, amount));
        return cfg;
    }
}
