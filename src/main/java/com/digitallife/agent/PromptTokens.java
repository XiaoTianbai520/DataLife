package com.digitallife.agent;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** 使用 LFM3-Omni 随包 tokenizer 计算文本预算。 */
@Component
public class PromptTokens {

    private final HuggingFaceTokenizer tokenizer;

    public PromptTokens() throws IOException {
        try (InputStream resource = new ClassPathResource("model/lfm3-omni-tokenizer.json.gz").getInputStream();
             InputStream json = new GZIPInputStream(resource)) {
            tokenizer = HuggingFaceTokenizer.newInstance(json, Map.of("truncation", "false", "padding", "false"));
        }
    }

    int count(String text) {
        return tokenizer.encode(text, false, false).getIds().length;
    }

    String clip(String text, int limit) {
        if (text == null || text.isEmpty() || limit <= 0) {
            return "";
        }
        String safeText = text.replace("<|", "＜|");
        long[] ids = tokenizer.encode(safeText, false, false).getIds();
        if (ids.length <= limit) {
            return safeText;
        }
        for (int length = limit; length > 0; length--) {
            String prefix = tokenizer.decode(Arrays.copyOf(ids, length), false);
            if (!prefix.endsWith("�") && count(prefix) <= limit) {
                return prefix.stripTrailing();
            }
        }
        return "";
    }

    @PreDestroy
    void close() {
        tokenizer.close();
    }
}
