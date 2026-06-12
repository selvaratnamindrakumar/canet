package com.canet.keypoc;

import com.canet.keypoc.neo.NeoPatternRegistry;
import com.canet.keypoc.neo.NeoQueryPattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates Catherine's classification:
 *   "Easy" patterns: DynamoDB composite key handles them without app-side filtering.
 *   "Hard" patterns: require GSIs, multiple queries, or additional infrastructure.
 *
 * Aurora handles ALL patterns.
 */
class NeoPatternClassificationTest {

    private final NeoPatternRegistry registry = new NeoPatternRegistry();

    @Test
    void allPatternsDefined() {
        assertThat(registry.allPatterns()).hasSize(8);
    }

    @Test
    void dynamoEasyPatterns_areKeyBasedLookups() {
        List<NeoQueryPattern> easy = registry.allPatterns().stream()
                .filter(NeoQueryPattern::isDynamoFriendly)
                .toList();

        // CGI full record, DEVICE family, exact MODEL cell = 3 easy patterns
        assertThat(easy).hasSize(3);
        assertThat(easy).allMatch(p ->
                p.getDynamoDifficulty() == NeoQueryPattern.Difficulty.EASY_BOTH);
    }

    @Test
    void dynamoHardPatterns_requireGsiOrInfrastructure() {
        List<NeoQueryPattern> hard = registry.allPatterns().stream()
                .filter(p -> !p.isDynamoFriendly())
                .toList();

        assertThat(hard).hasSize(5);
        // None of the hard patterns should be EASY_BOTH
        assertThat(hard).noneMatch(p ->
                p.getDynamoDifficulty() == NeoQueryPattern.Difficulty.EASY_BOTH);
    }

    @Test
    void auroraHandlesAllPatterns() {
        // Aurora PostgreSQL has a non-empty operation description for every pattern
        assertThat(registry.allPatterns()).allMatch(p ->
                p.getAuroraOperation() != null && !p.getAuroraOperation().isBlank());
    }

    @Test
    void crossRowPatterns_areHardInDynamo() {
        // "Find all CGIs with MODEL=iPhone16" and similar cross-row queries
        List<NeoQueryPattern> crossRow = registry.allPatterns().stream()
                .filter(p -> p.getDynamoDifficulty() == NeoQueryPattern.Difficulty.HARD_IN_DYNAMO
                          || p.getDynamoDifficulty() == NeoQueryPattern.Difficulty.REQUIRES_EXTRA_INFRA_IN_DYNAMO)
                .toList();

        assertThat(crossRow).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void timestampRangePattern_flaggedAsNeedsGsi() {
        NeoQueryPattern versioned = registry.allPatterns().stream()
                .filter(p -> p.getName().contains("Versioned"))
                .findFirst()
                .orElseThrow();

        assertThat(versioned.isUsesTimestampFilter()).isTrue();
        assertThat(versioned.isDynamoFriendly()).isFalse();
        assertThat(versioned.getNotes()).contains("zero-padded");
    }

    @Test
    void compositeKeyPatterns_doNotUseAppSideFiltering() {
        // The 3 easy patterns explicitly state server-side filtering
        registry.allPatterns().stream()
                .filter(NeoQueryPattern::isDynamoFriendly)
                .forEach(p -> {
                    // DynamoDB operation should mention begins_with or Query — not "filter in application"
                    assertThat(p.getDynamoOperation()).containsAnyOf("begins_with", "Query PK");
                });
    }
}
