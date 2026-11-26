package com.example.javaweb.agent.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class EmbeddingCodecTest {

    @Test
    void bytesRoundTrip() {
        float[] vector = {0.5f, -1.25f, 3.0f, 0f};
        byte[] bytes = EmbeddingCodec.toBytes(vector);

        assertThat(bytes).hasSize(vector.length * Float.BYTES);
        assertThat(EmbeddingCodec.toVector(bytes)).containsExactly(vector);
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertThat(EmbeddingCodec.toBytes(null)).isNull();
        assertThat(EmbeddingCodec.toBytes(new float[0])).isNull();
        assertThat(EmbeddingCodec.toVector(null)).isNull();
        assertThat(EmbeddingCodec.toVector(new byte[0])).isNull();
    }

    @Test
    void normalizeProducesUnitLength() {
        float[] normalized = EmbeddingCodec.normalize(new float[]{3f, 4f});
        assertThat(normalized[0]).isCloseTo(0.6f, offset(1e-6f));
        assertThat(normalized[1]).isCloseTo(0.8f, offset(1e-6f));
    }

    @Test
    void cosineOfSameDirectionIsOne() {
        float[] vector = EmbeddingCodec.normalize(new float[]{1f, 2f, 3f});
        assertThat(EmbeddingCodec.cosine(vector, EmbeddingCodec.toBytes(vector)))
                .isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void cosineOfOrthogonalVectorsIsZero() {
        float[] left = {1f, 0f};
        float[] right = {0f, 1f};
        assertThat(EmbeddingCodec.cosine(left, EmbeddingCodec.toBytes(right)))
                .isCloseTo(0.0, offset(1e-9));
    }

    @Test
    void cosineHandlesMissingOrMismatchedVectors() {
        assertThat(EmbeddingCodec.cosine((float[]) null, new byte[4])).isZero();
        assertThat(EmbeddingCodec.cosine(new float[]{1f}, null)).isZero();
        assertThat(EmbeddingCodec.cosine(new float[]{1f}, EmbeddingCodec.toBytes(new float[]{1f, 2f})))
                .isZero();
    }
}
