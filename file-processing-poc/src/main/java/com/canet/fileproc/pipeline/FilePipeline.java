package com.canet.fileproc.pipeline;

import com.canet.fileproc.model.IngestRecord;
import com.canet.fileproc.model.ProcessingOutcome;
import com.canet.fileproc.model.SourceType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FilePipeline {

    private final SourceType sourceType;
    private Function<Path, Stream<IngestRecord>> parser;
    private Predicate<IngestRecord> validator = r -> true;
    private Predicate<IngestRecord> additionalFilter = r -> true;
    private Function<IngestRecord, IngestRecord> transformer = Function.identity();
    private Consumer<Stream<IngestRecord>> sink = stream -> stream.forEach(ignored -> {});

    private FilePipeline(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public static FilePipeline of(SourceType sourceType) {
        return new FilePipeline(sourceType);
    }

    public FilePipeline parse(Function<Path, Stream<IngestRecord>> parser) {
        this.parser = parser;
        return this;
    }

    public FilePipeline validate(Predicate<IngestRecord> validator) {
        this.validator = validator;
        return this;
    }

    public FilePipeline filter(Predicate<IngestRecord> filter) {
        this.additionalFilter = filter;
        return this;
    }

    public FilePipeline transform(Function<IngestRecord, IngestRecord> transformer) {
        this.transformer = transformer;
        return this;
    }

    public FilePipeline sink(Consumer<Stream<IngestRecord>> sink) {
        this.sink = sink;
        return this;
    }

    public ProcessingOutcome execute(Path inputPath) {
        long start = System.currentTimeMillis();
        String filename = inputPath.getFileName().toString();

        AtomicInteger parsedCount = new AtomicInteger();
        AtomicInteger validCount = new AtomicInteger();
        AtomicInteger invalidCount = new AtomicInteger();
        AtomicInteger writtenCount = new AtomicInteger();

        List<IngestRecord> validRecords = new ArrayList<>();

        parser.apply(inputPath)
                .peek(r -> parsedCount.incrementAndGet())
                .forEach(record -> {
                    if (validator.test(record) && additionalFilter.test(record)) {
                        validCount.incrementAndGet();
                        validRecords.add(transformer.apply(record));
                    } else {
                        invalidCount.incrementAndGet();
                    }
                });

        Stream<IngestRecord> outputStream = validRecords.stream()
                .peek(r -> writtenCount.incrementAndGet());

        sink.accept(outputStream);

        return new ProcessingOutcome(
                filename,
                parsedCount.get(),
                validCount.get(),
                invalidCount.get(),
                writtenCount.get(),
                System.currentTimeMillis() - start
        );
    }
}
