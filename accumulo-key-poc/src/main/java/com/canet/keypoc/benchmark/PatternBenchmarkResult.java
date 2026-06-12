package com.canet.keypoc.benchmark;

import com.canet.keypoc.neo.NeoQueryPattern;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Side-by-side comparison of one NEO query pattern executed against both backends.
 */
@Data
@Builder
public class PatternBenchmarkResult {

    private String patternName;
    private String accumuloOperation;

    private QueryResult dynamoResult;
    private QueryResult auroraResult;

    private boolean resultsMatch;
    private NeoQueryPattern.Difficulty dynamoDifficulty;
    private boolean dynamoFriendly;
    private String recommendation;
    private String notes;
}
