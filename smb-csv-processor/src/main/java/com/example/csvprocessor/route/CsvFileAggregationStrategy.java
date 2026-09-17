package com.example.csvprocessor.route;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Camel aggregation strategy that collects CSV {@link File} objects from
 * multiple exchanges into a single {@code List<File>}.
 *
 * <p>Used by {@link CsvProcessingRoute} so that all CSV files arriving in
 * one poll cycle are processed together, producing a single merged output
 * file rather than one output file per input CSV.
 */
public class CsvFileAggregationStrategy implements AggregationStrategy {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        File incomingFile = newExchange.getIn().getBody(File.class);

        if (oldExchange == null) {
            // First file — start a new list
            List<File> files = new ArrayList<>();
            files.add(incomingFile);
            newExchange.getIn().setBody(files);
            return newExchange;
        }

        @SuppressWarnings("unchecked")
        List<File> files = oldExchange.getIn().getBody(List.class);
        files.add(incomingFile);
        return oldExchange;
    }
}
