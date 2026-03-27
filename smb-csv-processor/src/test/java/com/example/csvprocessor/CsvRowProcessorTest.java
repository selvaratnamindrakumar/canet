package com.example.csvprocessor;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.model.FieldMapping;
import com.example.csvprocessor.model.InputFieldRef;
import com.example.csvprocessor.model.MappingConfiguration;
import com.example.csvprocessor.model.ProcessingResult;
import com.example.csvprocessor.model.RangeRule;
import com.example.csvprocessor.model.ValidationRule;
import com.example.csvprocessor.service.BngConverter;
import com.example.csvprocessor.service.CsvRowProcessor;
import com.example.csvprocessor.service.FormulaService;
import com.example.csvprocessor.service.MappingConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CsvRowProcessor} — no Spring context needed.
 *
 * <p>Covers:
 * <ul>
 *   <li>Valid rows routed to success file in IE/AA quoted-uppercase format</li>
 *   <li>Invalid rows (nullable violation, range violation) routed to quarantine</li>
 *   <li>No quarantine file created when every row is valid</li>
 *   <li>Multi-input formula field (deriveEci)</li>
 *   <li>Range validation via {@link RangeRule}</li>
 * </ul>
 */
class CsvRowProcessorTest {

    @TempDir
    Path tempDir;

    private CsvRowProcessor processor;
    private MappingConfigService mockMappingService;

    @BeforeEach
    void setUp() {
        processor = new CsvRowProcessor();

        mockMappingService = Mockito.mock(MappingConfigService.class);
        ReflectionTestUtils.setField(processor, "mappingConfigService", mockMappingService);

        // Wire a real FormulaService backed by a real BngConverter
        BngConverter bngConverter = new BngConverter();
        FormulaService formulaService = new FormulaService();
        ReflectionTestUtils.setField(formulaService, "bngConverter", bngConverter);
        ReflectionTestUtils.setField(processor, "formulaService", formulaService);

        DirectoryProperties dirs = new DirectoryProperties();
        ReflectionTestUtils.setField(dirs, "outputSuccess",    tempDir.resolve("success").toString());
        ReflectionTestUtils.setField(dirs, "outputQuarantine", tempDir.resolve("quarantine").toString());
        ReflectionTestUtils.setField(processor, "directoryProperties", dirs);

        tempDir.resolve("success").toFile().mkdirs();
        tempDir.resolve("quarantine").toFile().mkdirs();
    }

    // -------------------------------------------------------------------------
    // Test: valid rows → success file, IE/AA quoted-uppercase format
    // -------------------------------------------------------------------------

    @Test
    void validRows_goToSuccessFile_quotedUppercase() throws Exception {
        Mockito.when(mockMappingService.getConfiguration()).thenReturn(buildTelecomConfig());

        File csv = writeCsv("mcc,mnc,generation\n"
                + "234,30,4G\n"
                + "234,20,5G\n");

        ProcessingResult result = processor.processFile(csv);

        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getQuarantineCount());
        assertNotNull(result.getSuccessFile());
        assertNull(result.getQuarantineFile());

        // Verify IE/AA quoted-uppercase format
        List<String> lines = Files.readAllLines(result.getSuccessFile().toPath(),
                StandardCharsets.UTF_8);
        assertTrue(lines.get(0).startsWith("\"MCC\",\"MNC\""),
                "Header must be quoted uppercase: " + lines.get(0));
        assertTrue(lines.get(1).startsWith("\"234\""),
                "Values must be quoted: " + lines.get(1));
    }

    // -------------------------------------------------------------------------
    // Test: nullable=false violation → quarantine
    // -------------------------------------------------------------------------

    @Test
    void nullableViolation_goToQuarantineFile() throws Exception {
        Mockito.when(mockMappingService.getConfiguration()).thenReturn(buildTelecomConfig());

        File csv = writeCsv("mcc,mnc,generation\n"
                + "234,30,4G\n"       // valid
                + ",30,4G\n"          // invalid — mcc is nullable=false
                + "234,30,\n");       // invalid — generation is nullable=false

        ProcessingResult result = processor.processFile(csv);

        assertEquals(3, result.getTotalRows());
        assertEquals(1, result.getSuccessCount());
        assertEquals(2, result.getQuarantineCount());
        assertNotNull(result.getQuarantineFile());
    }

    // -------------------------------------------------------------------------
    // Test: range validation failure → quarantine
    // -------------------------------------------------------------------------

    @Test
    void rangeViolation_goToQuarantineFile() throws Exception {
        Mockito.when(mockMappingService.getConfiguration()).thenReturn(buildTelecomConfig());

        File csv = writeCsv("mcc,mnc,generation\n"
                + "1000,30,4G\n"    // invalid — mcc > 999
                + "0,30,4G\n"       // invalid — mcc < 1
                + "234,30,4G\n");   // valid

        ProcessingResult result = processor.processFile(csv);

        assertEquals(3, result.getTotalRows());
        assertEquals(1, result.getSuccessCount());
        assertEquals(2, result.getQuarantineCount());
    }

    // -------------------------------------------------------------------------
    // Test: all rows valid → no quarantine file created
    // -------------------------------------------------------------------------

    @Test
    void allRowsValid_noQuarantineFileCreated() throws Exception {
        Mockito.when(mockMappingService.getConfiguration()).thenReturn(buildTelecomConfig());

        File csv = writeCsv("mcc,mnc,generation\n"
                + "234,30,4G\n");

        ProcessingResult result = processor.processFile(csv);

        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getQuarantineCount());
        assertNull(result.getQuarantineFile(), "Quarantine file must not be created when all rows are valid");
    }

    // -------------------------------------------------------------------------
    // Test: formula field (deriveEci) — multi-input
    // -------------------------------------------------------------------------

    @Test
    void formulaField_deriveEci_computedCorrectly() throws Exception {
        MappingConfiguration cfg = new MappingConfiguration();
        cfg.setHasHeader(true);
        cfg.setInputDelimiter(",");
        cfg.setOutputDelimiter(",");
        cfg.setEncoding("UTF-8");
        cfg.setOutputFormat("STANDARD");

        FieldMapping enodeb = new FieldMapping();
        enodeb.setSourceColumnName("enodeb_id");
        enodeb.setTargetColumnName("ENODEB_ID");
        enodeb.setExpectedType("INTEGER");
        enodeb.setNullable(true);
        enodeb.setTransformations(Collections.singletonList("TRIM"));

        FieldMapping cellId = new FieldMapping();
        cellId.setSourceColumnName("cell_id");
        cellId.setTargetColumnName("CELL_ID");
        cellId.setExpectedType("INTEGER");
        cellId.setNullable(true);
        cellId.setTransformations(Collections.singletonList("TRIM"));

        // Formula field: ECI = enodeb_id * 256 + (cell_id & 0xFF)
        FieldMapping eci = new FieldMapping();
        eci.setTargetColumnName("DERIVED_ECI");
        eci.setFormula("deriveEci");
        eci.setExpectedType("INTEGER");
        eci.setNullable(true);
        InputFieldRef refEnodeb = new InputFieldRef(); refEnodeb.setName("enodeb_id");
        InputFieldRef refCell   = new InputFieldRef(); refCell.setName("cell_id");
        eci.setInputFields(Arrays.asList(refEnodeb, refCell));

        cfg.setFields(Arrays.asList(enodeb, cellId, eci));

        Mockito.when(mockMappingService.getConfiguration()).thenReturn(cfg);

        // enodeb_id=1000, cell_id=5 → ECI = 1000 * 256 + 5 = 256005
        File csv = writeCsv("enodeb_id,cell_id\n1000,5\n");
        ProcessingResult result = processor.processFile(csv);

        assertEquals(1, result.getSuccessCount());
        List<String> lines = Files.readAllLines(result.getSuccessFile().toPath(),
                StandardCharsets.UTF_8);
        // line[1] = data row: ENODEB_ID,CELL_ID,DERIVED_ECI → 1000,5,256005
        assertTrue(lines.get(1).endsWith("256005"),
                "ECI should be 256005 but got: " + lines.get(1));
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

    /**
     * Minimal telecom mapping: mcc (INTEGER, nullable=false, range 1–999),
     * mnc (INTEGER, nullable=true, range 0–999), generation (STRING, nullable=false).
     */
    private MappingConfiguration buildTelecomConfig() {
        MappingConfiguration cfg = new MappingConfiguration();
        cfg.setHasHeader(true);
        cfg.setInputDelimiter(",");
        cfg.setOutputDelimiter(",");
        cfg.setEncoding("UTF-8");
        cfg.setOutputFormat("QUOTED_UPPERCASE");

        FieldMapping mcc = new FieldMapping();
        mcc.setSourceColumnName("mcc");
        mcc.setTargetColumnName("MCC");
        mcc.setExpectedType("INTEGER");
        mcc.setNullable(false);
        mcc.setTransformations(Collections.singletonList("TRIM"));
        ValidationRule mccRule = new ValidationRule();
        RangeRule mccRange = new RangeRule();
        mccRange.setMin("1");
        mccRange.setMax("999");
        mccRule.setRange(mccRange);
        mcc.setValidation(mccRule);

        FieldMapping mnc = new FieldMapping();
        mnc.setSourceColumnName("mnc");
        mnc.setTargetColumnName("MNC");
        mnc.setExpectedType("INTEGER");
        mnc.setNullable(true);
        mnc.setTransformations(Collections.singletonList("TRIM"));
        ValidationRule mncRule = new ValidationRule();
        RangeRule mncRange = new RangeRule();
        mncRange.setMin("0");
        mncRange.setMax("999");
        mncRule.setRange(mncRange);
        mnc.setValidation(mncRule);

        FieldMapping generation = new FieldMapping();
        generation.setSourceColumnName("generation");
        generation.setTargetColumnName("GENERATION");
        generation.setExpectedType("STRING");
        generation.setNullable(false);
        generation.setTransformations(Arrays.asList("TRIM", "UPPERCASE"));
        ValidationRule genRule = new ValidationRule();
        genRule.setAllowedValues(Arrays.asList("2G", "3G", "4G", "5G"));
        generation.setValidation(genRule);

        cfg.setFields(Arrays.asList(mcc, mnc, generation));
        return cfg;
    }
}
