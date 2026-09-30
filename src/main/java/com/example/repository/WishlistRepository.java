package com.example.repository;

import com.example.entity.WishlistItemEntity;
import com.example.model.WishlistItem;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Inject;
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
    private final DataSource dataSource;
    private final WishlistWriteRepository writeRepository;

    @Inject
    public WishlistRepository(DataSource dataSource, WishlistWriteRepository writeRepository) {
        this.dataSource = dataSource;
        this.writeRepository = writeRepository;
    }

    // Retains the lightweight constructor used by tests that do not exercise wishlist writes.
    public WishlistRepository(DataSource dataSource) {
        this(dataSource, null);
    }

    public boolean save(String conversationId, WishlistItem item) {
        try {
            writeRepository.upsert(new WishlistItemEntity(
                conversationId, item.itemType(), item.itemId()));
            return true;
        } catch (RuntimeException e) {
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
