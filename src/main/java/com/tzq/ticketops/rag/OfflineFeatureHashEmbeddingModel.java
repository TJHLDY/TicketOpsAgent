package com.tzq.ticketops.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OfflineFeatureHashEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 256;

    private static final Map<String, String> SEMANTIC_NORMALIZATION = semanticNormalization();

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        for (int index = 0; index < request.getInstructions().size(); index++) {
            embeddings.add(new Embedding(vectorize(request.getInstructions().get(index)), index));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorize(getEmbeddingContent(document));
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] vectorize(String input) {
        String normalized = normalize(input);
        float[] vector = new float[DIMENSIONS];

        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (token.isBlank()) {
                continue;
            }
            addFeature(vector, "word:" + token, 2.0f);
            if (isLatinToken(token)) {
                addNgrams(vector, token, 3, 4, "latin:", 0.7f);
            } else {
                addNgrams(vector, token, 1, 3, "cjk:", 1.0f);
            }
        }

        normalize(vector);
        return vector;
    }

    private String normalize(String input) {
        String value = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : SEMANTIC_NORMALIZATION.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }
        return value;
    }

    private boolean isLatinToken(String token) {
        return token.codePoints().allMatch(codePoint -> codePoint < 128);
    }

    private void addNgrams(
            float[] vector,
            String token,
            int minSize,
            int maxSize,
            String prefix,
            float weight
    ) {
        int[] codePoints = token.codePoints().toArray();
        for (int size = minSize; size <= maxSize; size++) {
            for (int start = 0; start + size <= codePoints.length; start++) {
                String ngram = new String(codePoints, start, size);
                addFeature(vector, prefix + ngram, weight);
            }
        }
    }

    private void addFeature(float[] vector, String feature, float weight) {
        int hash = feature.hashCode();
        int index = Math.floorMod(hash, DIMENSIONS);
        int mixed = hash ^ (hash >>> 16);
        vector[index] += (mixed & 1) == 0 ? weight : -weight;
    }

    private void normalize(float[] vector) {
        double squaredSum = 0;
        for (float value : vector) {
            squaredSum += value * value;
        }
        if (squaredSum == 0) {
            vector[0] = 1;
            return;
        }
        float norm = (float) Math.sqrt(squaredSum);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= norm;
        }
    }

    private static Map<String, String> semanticNormalization() {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("bypass approval", "绕过审批");
        replacements.put("privilege escalation", "越权");
        replacements.put("administrator permission", "管理员权限");
        replacements.put("admin permission", "管理员权限");
        replacements.put("verification code", "验证码");
        replacements.put("multi-factor", "多因素验证");
        replacements.put("multi factor", "多因素验证");
        replacements.put("request access", "权限申请");
        replacements.put("access denied", "无权访问");
        replacements.put("no access", "无权访问");
        replacements.put("sign in", "登录");
        replacements.put("log in", "登录");
        replacements.put("account", "账号");
        replacements.put("locked", "锁定");
        replacements.put("permission", "权限");
        replacements.put("authenticator", "验证器");
        replacements.put("login", "登录");
        replacements.put("unlock", "解锁");
        replacements.put("mfa", "多因素验证");
        return Collections.unmodifiableMap(replacements);
    }
}
