package com.example.repository;

import com.example.model.WishlistItem;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Connectable
public class WishlistRepository {
    private static final Logger LOG = LoggerFactory.getLogger(WishlistRepository.class);
    private static final String SELECT_WISHLIST = """
        SELECT w.id, w.item_type, w.item_id,
               CASE w.item_type
                   WHEN 'destination' THEN (SELECT d.name FROM destinations d WHERE d.id = w.item_id)
                   WHEN 'hotel' THEN (SELECT h.name FROM hotels h WHERE h.id = w.item_id)
                   WHEN 'activity' THEN (SELECT a.name FROM activities a WHERE a.id = w.item_id)
               END AS item_name,
               CASE w.item_type
                   WHEN 'destination' THEN (SELECT d.region FROM destinations d WHERE d.id = w.item_id)
                   WHEN 'hotel' THEN (SELECT 'CHF ' || TO_CHAR(h.price_per_night, 'FM9999990') || ' / night' FROM hotels h WHERE h.id = w.item_id)
                   WHEN 'activity' THEN (SELECT a.season FROM activities a WHERE a.id = w.item_id)
               END AS item_detail
        FROM wishlist_items w
        WHERE w.conversation_id = ?
        ORDER BY w.id DESC
        """;
    private static final String MERGE_WISHLIST = """
        MERGE INTO wishlist_items target
        USING (SELECT ? AS conversation_id, ? AS item_type, ? AS item_id FROM dual) source
        ON (target.conversation_id = source.conversation_id
            AND target.item_type = source.item_type
            AND target.item_id = source.item_id)
        WHEN NOT MATCHED THEN
            INSERT (conversation_id, item_type, item_id)
            VALUES (source.conversation_id, source.item_type, source.item_id)
        """;

    private final DataSource dataSource;

    public WishlistRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean save(String conversationId, WishlistItem item) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(MERGE_WISHLIST)) {
            stmt.setString(1, conversationId);
            stmt.setString(2, item.itemType());
            stmt.setLong(3, item.itemId());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOG.error("Error saving wishlist item", e);
            return false;
        }
    }

    public List<WishlistItem> findAll(String conversationId) {
        List<WishlistItem> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_WISHLIST)) {
            stmt.setString(1, conversationId);
            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    results.add(mapWishlistItem(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error finding all wishlist items", e);
        }
        return results;
    }

    public void deleteAll(String conversationId) {
        String sql = "DELETE FROM wishlist_items WHERE conversation_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, conversationId);
            int deleted = stmt.executeUpdate();
            LOG.debug("Deleted {} wishlist items for conversation {}", deleted, conversationId);
        } catch (SQLException e) {
            LOG.error("Error deleting all wishlist items", e);
        }
    }

    private WishlistItem mapWishlistItem(ResultSet rs) throws SQLException {
        return new WishlistItem(
            rs.getLong("id"),
            rs.getString("item_type"),
            rs.getLong("item_id"),
            rs.getString("item_name"),
            rs.getString("item_detail")
        );
    }
}
