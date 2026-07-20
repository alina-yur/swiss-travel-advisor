package com.example.memory;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("app.chat-memory.oracle")
public class OracleChatMemoryConfiguration {
    private String tableName = "CHAT_MEMORY";
    private String memoryIdColumnName = "MEMORY_ID";
    private String contentColumnName = "CONTENT";

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getMemoryIdColumnName() {
        return memoryIdColumnName;
    }

    public void setMemoryIdColumnName(String memoryIdColumnName) {
        this.memoryIdColumnName = memoryIdColumnName;
    }

    public String getContentColumnName() {
        return contentColumnName;
    }

    public void setContentColumnName(String contentColumnName) {
        this.contentColumnName = contentColumnName;
    }
}
