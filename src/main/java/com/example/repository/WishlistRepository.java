package com.example.repository;

import com.example.model.WishlistItem;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class WishlistRepository {
    private static final Logger LOG = LoggerFactory.getLogger(WishlistRepository.class);
    private static final String SELECT_WISHLIST = "SELECT id, item_type, item_id FROM wishlist_items";

    private final DataSource dataSource;

    public WishlistRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public WishlistItem save(WishlistItem item) {
        String sql = "INSERT INTO wishlist_items (item_type, item_id) VALUES (?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, new String[]{"id"})) {

            stmt.setString(1, item.itemType());
            stmt.setLong(2, item.itemId());
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new WishlistItem(rs.getLong(1), item.itemType(), item.itemId());
                }
            }
        } catch (SQLException e) {
            LOG.error("Error saving wishlist item", e);
        }
        return null;
    }

    public List<WishlistItem> findAll() {
        List<WishlistItem> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_WISHLIST);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                results.add(mapWishlistItem(rs));
            }
        } catch (SQLException e) {
            LOG.error("Error finding all wishlist items", e);
        }
        return results;
    }

    public void deleteAll() {
        String sql = "DELETE FROM wishlist_items";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int deleted = stmt.executeUpdate();
            LOG.debug("Deleted {} wishlist items", deleted);
        } catch (SQLException e) {
            LOG.error("Error deleting all wishlist items", e);
        }
    }

    private WishlistItem mapWishlistItem(ResultSet rs) throws SQLException {
        return new WishlistItem(
            rs.getLong("id"),
            rs.getString("item_type"),
            rs.getLong("item_id")
        );
    }
}
