package com.canet.keypoc.neo;

import com.canet.keypoc.scanner.AccumuloScannerOp;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Documents a single NEO API operation: what Accumulo scanner call it makes,
 * what fields it expects back, and what post-retrieval logic it applies.
 *
 * This is the key discovery artefact of the POC.
 * Populate one of these per distinct NEO API call pattern found in the codebase.
 * The answer drives the DynamoDB vs Aurora recommendation.
 */
@Data
@Builder
public class NeoQueryPattern {

    public enum Difficulty {
        /** PK-only or PK+SK lookup — DynamoDB and Aurora equally efficient. */
        EASY_BOTH,
        /** Requires GSI in DynamoDB; standard indexed query in Aurora. */
        NEEDS_GSI_IN_DYNAMO,
        /** Requires full table scan in DynamoDB without GSI; trivial in Aurora. */
        HARD_IN_DYNAMO,
        /** Requires DynamoDB Streams + Lambda or separate index table to support. */
        REQUIRES_EXTRA_INFRA_IN_DYNAMO
    }

    /** Human-readable name for this access pattern (e.g. "CGI device lookup") */
    private String name;

    /** What the NEO code currently does in Accumulo terms */
    private String accumuloOperation;

    /** The scanner op this translates to */
    private AccumuloScannerOp scannerOp;

    /** DynamoDB equivalent operation description */
    private String dynamoOperation;

    /** Aurora SQL equivalent */
    private String auroraOperation;

    /** Fields the caller expects back — used to assess whether projection is possible */
    private List<String> requiredFields;

    /** Whether family/qualifier are used for server-side filtering in this pattern */
    private boolean usesFamilyQualifierFilter;

    /** Whether timestamp range filtering is used */
    private boolean usesTimestampFilter;

    /** How hard is this pattern to implement correctly in DynamoDB? */
    private Difficulty dynamoDifficulty;

    /** Is this an "easy" DynamoDB pattern (pure CGI lookup, composite SK query) */
    private boolean dynamoFriendly;

    /** Post-retrieval logic applied in the application after the database read */
    private String postRetrievalLogic;

    /** Any notes / caveats discovered during the POC */
    private String notes;
}
