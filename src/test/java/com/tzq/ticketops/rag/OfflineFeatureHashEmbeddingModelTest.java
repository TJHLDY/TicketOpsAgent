package com.tzq.ticketops.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineFeatureHashEmbeddingModelTest {

    @Test
    void producesDeterministicNormalizedQueryDependentVectors() {
        OfflineFeatureHashEmbeddingModel model = new OfflineFeatureHashEmbeddingModel();

        float[] locked = model.embed("OA account is locked");
        float[] lockedAgain = model.embed("OA account is locked");
        float[] permission = model.embed("request CRM permission");

        assertThat(locked).hasSize(OfflineFeatureHashEmbeddingModel.DIMENSIONS);
        assertThat(lockedAgain).containsExactly(locked);
        assertThat(cosine(locked, permission)).isLessThan(0.95);
        assertThat(l2Norm(locked)).isCloseTo(1.0, within(0.0001));
    }

    @Test
    void semanticNormalizationBringsBilingualAccountLockTextCloserThanUnrelatedText() {
        OfflineFeatureHashEmbeddingModel model = new OfflineFeatureHashEmbeddingModel();
        float[] chineseLocked = model.embed("账号已锁定，无法登录 OA");

        double englishLockedSimilarity = cosine(chineseLocked, model.embed("OA account locked sign in failure"));
        double unrelatedSimilarity = cosine(chineseLocked, model.embed("How do I cook tomato pasta?"));

        assertThat(englishLockedSimilarity).isGreaterThan(unrelatedSimilarity);
    }

    private double l2Norm(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
        }
        return dot;
    }

    private org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
