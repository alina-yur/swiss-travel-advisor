package com.example.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    public OracleChatMemoryStore(DataSource dataSource, OracleChatMemoryConfiguration configuration) {
        this.dataSource = dataSource;

        String tableName = validateIdentifier(configuration.getTableName(), "table-name");
        String memoryIdColumnName = validateIdentifier(configuration.getMemoryIdColumnName(), "memory-id-column-name");
        String contentColumnName = validateIdentifier(configuration.getContentColumnName(), "content-column-name");

        this.selectSql = "SELECT " + contentColumnName + " FROM " + tableName
            + " WHERE " + memoryIdColumnName + " = ?";
        this.mergeSql = "MERGE INTO " + tableName + " target "
            + "USING (SELECT ? AS " + memoryIdColumnName + ", ? AS " + contentColumnName + " FROM dual) source "
            + "ON (target." + memoryIdColumnName + " = source." + memoryIdColumnName + ") "
            + "WHEN MATCHED THEN UPDATE SET target." + contentColumnName + " = source." + contentColumnName + " "
            + "WHEN NOT MATCHED THEN INSERT (" + memoryIdColumnName + ", " + contentColumnName + ") "
            + "VALUES (source." + memoryIdColumnName + ", source." + contentColumnName + ")";
        this.deleteSql = "DELETE FROM " + tableName + " WHERE " + memoryIdColumnName + " = ?";
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
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update chat memory for conversation " + id, e);
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

    private static String validateIdentifier(String identifier, String propertyName) {
        if (identifier == null || !SIMPLE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("app.chat-memory.oracle." + propertyName
                + " must be a simple Oracle identifier");
        }
        return identifier;
    }
}
