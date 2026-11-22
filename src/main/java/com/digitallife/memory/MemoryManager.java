package com.digitallife.memory;

import com.digitallife.domain.ChatMessage;

import java.util.List;

/**
 * 记忆管理核心接口（对应 api.md 1.2）。
 */
public interface MemoryManager {

    /** 获取最近 N 轮可见对话（默认 2~3 轮纯文本） */
    List<ChatMessage> getShortTermWindow();

    /** 根据当前输入与感官摘要，动态召回最相关的 Top-K 长期记忆 */
    List<String> retrieveLongTermMemories(String queryText, int topK);

    /** 记录一条可见对话（主人输入或 speak 输出） */
    ChatMessage recordVisibleMessage(String role, String content, Long thoughtLogId);

    /** 在后台处理主人明确陈述及当轮独白中的事实候选，再向量化入库。 */
    void asyncExtractMemory(String userInput, String modelThought, Long sourceThoughtId);
}
