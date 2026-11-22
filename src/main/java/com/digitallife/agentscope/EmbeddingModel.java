package com.digitallife.agentscope;

/**
 * 文本向量化模型接口，用于长期记忆 RAG 存储与余弦相似度检索。
 */
public interface EmbeddingModel {

    String vectorSpace();

    /**
     * 将输入文本转化为归一化浮点向量。
     */
    float[] embed(String text);

    default float[] embedQuery(String text) {
        return embed(text);
    }

    /**
     * 计算两个向量的余弦相似度。
     */
    default double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
