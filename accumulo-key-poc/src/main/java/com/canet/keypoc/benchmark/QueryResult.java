package com.canet.keypoc.benchmark;

import com.canet.keypoc.model.AccumuloCell;
import com.canet.keypoc.neo.NeoQueryPattern;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of running a single AccumuloScannerOp against one backend.
 */
@Data
@Builder
public class QueryResult {
    private String backend;
    private String operationDescription;
    private List<AccumuloCell> cells;
    private int cellCount;
    private long queryMs;
    private String sqlOrApiCall;      // what was actually sent to the database
    private boolean appSideFiltering; // true = data returned to Java then filtered (anti-pattern)
    private String notes;
}
