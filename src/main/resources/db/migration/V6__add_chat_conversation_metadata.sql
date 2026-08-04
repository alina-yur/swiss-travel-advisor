ALTER TABLE chat_memory ADD (
    title VARCHAR2(160),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_chat_memory_updated_at ON chat_memory(updated_at DESC);
