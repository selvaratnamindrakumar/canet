package com.canet.fileproc.controller;

import com.canet.fileproc.ingest.CsvIngester;
import com.canet.fileproc.ingest.HsIngester;
import com.canet.fileproc.ingest.TlvIngester;
import com.canet.fileproc.model.IngestRecord;
import com.canet.fileproc.model.ProcessingOutcome;
import com.canet.fileproc.model.SourceType;
import com.canet.fileproc.model.ValidationResult;
import com.canet.fileproc.pipeline.FilePipeline;
import com.canet.fileproc.pipeline.ValidationPipeline;
import com.canet.fileproc.processing.AgeOffProcessor;
import com.canet.fileproc.processing.DeltaExporter;
import com.canet.fileproc.processing.DiffCurator;
import com.canet.fileproc.processing.DiffResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/files")
public class FileProcessingController {

    private final CsvIngester csvIngester;
    private final TlvIngester tlvIngester;
    private final HsIngester hsIngester;
    private final DiffCurator diffCurator;
    private final DeltaExporter deltaExporter;
    private final AgeOffProcessor ageOffProcessor;
    private final ValidationPipeline validationPipeline;

    @Value("${file.processing.age-off.bb-retention-days:365}")
    private int bbRetentionDays;

    @Value("${file.processing.age-off.is-retention-days:180}")
    private int isRetentionDays;

    public FileProcessingController(CsvIngester csvIngester, TlvIngester tlvIngester,
                                    HsIngester hsIngester, DiffCurator diffCurator,
                                    DeltaExporter deltaExporter, AgeOffProcessor ageOffProcessor) {
        this.csvIngester = csvIngester;
        this.tlvIngester = tlvIngester;
        this.hsIngester = hsIngester;
        this.diffCurator = diffCurator;
        this.deltaExporter = deltaExporter;
        this.ageOffProcessor = ageOffProcessor;
        this.validationPipeline = ValidationPipeline.standard();
    }

    @PostMapping("/ingest/bb")
    public ResponseEntity<ProcessingOutcome> ingestBb(@RequestParam("file") MultipartFile file) throws IOException {
        Path tmp = stageToTemp(file);
        try {
            ProcessingOutcome outcome = FilePipeline.of(SourceType.BB)
                    .parse(csvIngester::parse)
                    .validate(validationPipeline)
                    .filter(ageOffProcessor.ageOffFilter(SourceType.BB, bbRetentionDays))
                    .sink(stream -> stream.forEach(ignored -> {}))
                    .execute(tmp);
            return ResponseEntity.ok(outcome);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @PostMapping("/ingest/is")
    public ResponseEntity<ProcessingOutcome> ingestIs(@RequestParam("file") MultipartFile file) throws IOException {
        Path tmp = stageToTemp(file);
        try {
            ProcessingOutcome outcome = FilePipeline.of(SourceType.IS)
                    .parse(tlvIngester::parse)
                    .validate(validationPipeline)
                    .filter(ageOffProcessor.ageOffFilter(SourceType.IS, isRetentionDays))
                    .sink(stream -> stream.forEach(ignored -> {}))
                    .execute(tmp);
            return ResponseEntity.ok(outcome);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @PostMapping("/ingest/hs")
    public ResponseEntity<ProcessingOutcome> ingestHs(@RequestParam("file") MultipartFile file) throws IOException {
        Path tmp = stageToTemp(file);
        try {
            ProcessingOutcome outcome = FilePipeline.of(SourceType.HS)
                    .parse(hsIngester::parse)
                    .validate(validationPipeline)
                    .filter(ageOffProcessor.ageOffFilter(SourceType.HS, 0))
                    .sink(stream -> stream.forEach(ignored -> {}))
                    .execute(tmp);
            return ResponseEntity.ok(outcome);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @PostMapping("/diff/bb")
    public ResponseEntity<DiffResult> diffBb(
            @RequestParam("current") MultipartFile current,
            @RequestParam("previous") MultipartFile previous) throws IOException {

        Path tmpCurrent = stageToTemp(current);
        Path tmpPrevious = stageToTemp(previous);
        try {
            Map<String, IngestRecord> currentSnapshot = diffCurator.toSnapshot(csvIngester.parse(tmpCurrent));
            Map<String, IngestRecord> previousSnapshot = diffCurator.toSnapshot(csvIngester.parse(tmpPrevious));
            return ResponseEntity.ok(diffCurator.diff(previousSnapshot, currentSnapshot));
        } finally {
            Files.deleteIfExists(tmpCurrent);
            Files.deleteIfExists(tmpPrevious);
        }
    }

    @PostMapping("/delta/is")
    public ResponseEntity<Map<String, Object>> deltaIs(
            @RequestParam("current") MultipartFile current,
            @RequestParam("previous") MultipartFile previous) throws IOException {

        Path tmpCurrent = stageToTemp(current);
        Path tmpPrevious = stageToTemp(previous);
        try {
            Map<String, IngestRecord> currentSnapshot = deltaExporter.toSnapshot(tlvIngester.parse(tmpCurrent));
            Map<String, IngestRecord> previousSnapshot = deltaExporter.toSnapshot(tlvIngester.parse(tmpPrevious));
            List<IngestRecord> delta = deltaExporter.exportDelta(previousSnapshot, currentSnapshot);
            return ResponseEntity.ok(Map.of(
                    "deltaCount", delta.size(),
                    "changedRecords", delta
            ));
        } finally {
            Files.deleteIfExists(tmpCurrent);
            Files.deleteIfExists(tmpPrevious);
        }
    }

    @PostMapping("/ageoff")
    public ResponseEntity<Map<String, Object>> ageOff(@RequestBody AgeOffRequest request,
                                                       @RequestParam("file") MultipartFile file) throws IOException {
        Path tmp = stageToTemp(file);
        try {
            Stream<IngestRecord> records = switch (request.sourceType()) {
                case BB -> csvIngester.parse(tmp);
                case IS -> tlvIngester.parse(tmp);
                case HS -> hsIngester.parse(tmp);
            };
            long count = ageOffProcessor.countEligibleForRemoval(records, request.sourceType(), request.retentionDays());
            return ResponseEntity.ok(Map.of(
                    "sourceType", request.sourceType(),
                    "retentionDays", request.retentionDays(),
                    "eligibleForRemoval", count
            ));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @PostMapping("/validate/bb")
    public ResponseEntity<List<ValidationResult>> validateBb(@RequestParam("file") MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String filename = file.getOriginalFilename();
        List<ValidationResult> results = csvIngester.parseContent(content)
                .map(record -> validationPipeline.validate(record, filename))
                .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    private Path stageToTemp(MultipartFile file) throws IOException {
        Path tmp = Files.createTempFile("neo-ingest-", "-" + file.getOriginalFilename());
        file.transferTo(tmp);
        return tmp;
    }
}
