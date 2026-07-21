package com.example.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.micronaut.data.connection.annotation.Connectable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Persists one JSON-serialized message window per conversation in Oracle Database.
 *
 * <p>This application-local implementation follows LangChain4j's
 * OracleChatMemoryStore design and can be replaced by the official integration
 * when Micronaut LangChain4j 2.1 is available to the project.</p>
 */
@Singleton
@Connectable
public class OracleChatMemoryStore implements ChatMemoryStore {
    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final DataSource dataSource;
    private final String selectSql;
    private final String mergeSql;
    private final String deleteSql;
    private final String listSql;
    private final String detailSql;

    public OracleChatMemoryStore(DataSource dataSource, OracleChatMemoryConfiguration configuration) {
        this.dataSource = dataSource;

        String tableName = validateIdentifier(configuration.getTableName(), "table-name");
        String memoryIdColumnName = validateIdentifier(configuration.getMemoryIdColumnName(), "memory-id-column-name");
        String contentColumnName = validateIdentifier(configuration.getContentColumnName(), "content-column-name");

        this.selectSql = "SELECT " + contentColumnName + " FROM " + tableName
            + " WHERE " + memoryIdColumnName + " = ?";
        this.mergeSql = "MERGE INTO " + tableName + " target "
            + "USING (SELECT ? AS " + memoryIdColumnName + ", ? AS " + contentColumnName + ", ? AS title FROM dual) source "
            + "ON (target." + memoryIdColumnName + " = source." + memoryIdColumnName + ") "
            + "WHEN MATCHED THEN UPDATE SET target." + contentColumnName + " = source." + contentColumnName
            + ", target.title = COALESCE(target.title, source.title), target.updated_at = CURRENT_TIMESTAMP "
            + "WHEN NOT MATCHED THEN INSERT (" + memoryIdColumnName + ", " + contentColumnName + ", title) "
            + "VALUES (source." + memoryIdColumnName + ", source." + contentColumnName + ", source.title)";
        this.deleteSql = "DELETE FROM " + tableName + " WHERE " + memoryIdColumnName + " = ?";
        this.listSql = "SELECT " + memoryIdColumnName + ", title, " + contentColumnName
            + ", created_at, updated_at FROM " + tableName + " ORDER BY updated_at DESC FETCH FIRST 50 ROWS ONLY";
        this.detailSql = "SELECT title, " + contentColumnName + " FROM " + tableName
            + " WHERE " + memoryIdColumnName + " = ?";
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String id = requireMemoryId(memoryId);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Collections.emptyList();
                }
                String json = resultSet.getString(1);
                return json == null || json.isBlank()
                    ? Collections.emptyList()
                    : ChatMessageDeserializer.messagesFromJson(json);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load chat memory for conversation " + id, e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String id = requireMemoryId(memoryId);
        if (messages == null) {
            throw new IllegalArgumentException("messages cannot be null");
        }

        String json = ChatMessageSerializer.messagesToJson(messages);
        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(mergeSql)) {
            statement.setString(1, id);
            statement.setString(2, json);
            statement.setString(3, titleFrom(messages));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update chat memory for conversation " + id, e);
        }
    }

    public List<ConversationSummary> listConversations() {
        List<ConversationSummary> conversations = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(listSql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                List<ChatMessage> messages = deserialize(resultSet.getString(3));
                conversations.add(new ConversationSummary(
                    resultSet.getString(1),
                    fallbackTitle(resultSet.getString(2), messages),
                    previewFrom(messages),
                    toInstant(resultSet.getTimestamp(4)),
                    toInstant(resultSet.getTimestamp(5))
                ));
            }
            return conversations;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list chat conversations", e);
        }
    }

    public ConversationDetail getConversation(String memoryId) {
        String id = requireMemoryId(memoryId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(detailSql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                List<ChatMessage> messages = deserialize(resultSet.getString(2));
                List<ConversationMessage> visibleMessages = messages.stream()
                    .map(OracleChatMemoryStore::toVisibleMessage)
                    .filter(java.util.Objects::nonNull)
                    .toList();
                return new ConversationDetail(
                    id,
                    fallbackTitle(resultSet.getString(1), messages),
                    visibleMessages
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load chat conversation " + id, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String id = requireMemoryId(memoryId);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(deleteSql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete chat memory for conversation " + id, e);
        }
    }

    private static String requireMemoryId(Object memoryId) {
        if (memoryId == null || memoryId.toString().isBlank()) {
            throw new IllegalArgumentException("memoryId cannot be blank");
        }
        return memoryId.toString();
    }

    private static List<ChatMessage> deserialize(String json) {
        return json == null || json.isBlank()
            ? Collections.emptyList()
            : ChatMessageDeserializer.messagesFromJson(json);
    }

    private static String titleFrom(List<ChatMessage> messages) {
        return messages.stream()
            .filter(UserMessage.class::isInstance)
            .map(UserMessage.class::cast)
            .filter(UserMessage::hasSingleText)
            .map(UserMessage::singleText)
            .map(String::trim)
            .filter(text -> !text.isEmpty())
            .map(text -> truncate(text, 80))
            .findFirst()
            .orElse(null);
    }

    private static String fallbackTitle(String storedTitle, List<ChatMessage> messages) {
        if (storedTitle != null && !storedTitle.isBlank()) {
            return storedTitle;
        }
        String derivedTitle = titleFrom(messages);
        return derivedTitle == null ? "New Swiss adventure" : derivedTitle;
    }

    private static String previewFrom(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ConversationMessage message = toVisibleMessage(messages.get(i));
            if (message != null && message.content() != null && !message.content().isBlank()) {
                return truncate(message.content().replaceAll("\\s+", " ").trim(), 96);
            }
        }
        return "No messages yet";
    }

    private static ConversationMessage toVisibleMessage(ChatMessage message) {
        if (message instanceof UserMessage user && user.hasSingleText()) {
            return new ConversationMessage("user", user.singleText());
        }
        if (message instanceof AiMessage ai && ai.text() != null && !ai.text().isBlank()) {
            return new ConversationMessage("assistant", ai.text());
        }
        return null;
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1).stripTrailing() + "…";
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    @Serdeable
    public record ConversationSummary(
        String conversationId,
        String title,
        String preview,
        Instant createdAt,
        Instant updatedAt
    ) {}

    @Serdeable
    public record ConversationMessage(String role, String content) {}

    @Serdeable
    public record ConversationDetail(
        String conversationId,
        String title,
        List<ConversationMessage> messages
    ) {}

    private static String validateIdentifier(String identifier, String propertyName) {
        if (identifier == null || !SIMPLE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("app.chat-memory.oracle." + propertyName
                + " must be a simple Oracle identifier");
        }
        return identifier;
    }
}
