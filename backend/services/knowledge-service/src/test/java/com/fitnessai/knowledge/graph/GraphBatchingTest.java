package com.fitnessai.knowledge.graph;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphBatchingTest {

    @Test
    void packsChunksUpToTheBatchLimitAndSkipsBlanks() {
        String big = "x".repeat(1500);
        List<String> batches = GraphService.batch(Arrays.asList(big, big, "  ", null, big, "tail"));

        assertThat(batches).hasSize(2);
        assertThat(batches.get(0)).hasSize(1500 * 2 + 2);  // two chunks + separator
        assertThat(batches.get(1)).endsWith("tail");
        assertThat(batches).allSatisfy(b -> assertThat(b.length()).isLessThanOrEqualTo(GraphService.BATCH_CHARS));
    }

    @Test
    void keysNormaliseForWholeWordMatching() {
        assertThat(GraphKeys.key("Barbell Back-Squat!")).isEqualTo("barbell back squat");
        assertThat(GraphKeys.source("user:7", "doc-1")).isEqualTo("user:7|doc-1");
    }
}
