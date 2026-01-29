package com.example.repository;

import com.example.model.Activity;
import jakarta.inject.Singleton;
import oracle.jdbc.OracleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class ActivityRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ActivityRepository.class);
    private static final String SELECT_ACTIVITY = """
        SELECT a.id, a.destination_id, d.name as destination_name,
               a.name, a.season, a.description
        FROM activities a
        JOIN destinations d ON a.destination_id = d.id
        """;

    private final DataSource dataSource;

    public ActivityRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Activity> searchByVector(float[] queryVector, Long destinationId, int limit) {
        String sql = SELECT_ACTIVITY + """
            WHERE a.description_embedding IS NOT NULL
            """ + (destinationId != null ? " AND a.destination_id = ?" : "") + """
             ORDER BY VECTOR_DISTANCE(a.description_embedding, ?, COSINE) FETCH FIRST ? ROWS ONLY
            """;

        List<Activity> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int paramIndex = 1;
            if (destinationId != null) {
                stmt.setLong(paramIndex++, destinationId);
            }
            stmt.setObject(paramIndex++, queryVector, OracleType.VECTOR);
            stmt.setInt(paramIndex, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapActivity(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error searching activities by vector", e);
        }
        return results;
    }

    public List<Activity> findAll() {
        List<Activity> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ACTIVITY);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                results.add(mapActivity(rs));
            }
        } catch (SQLException e) {
            LOG.error("Error finding all activities", e);
        }
        return results;
    }

    public List<Activity> findWithoutEmbedding() {
        String sql = SELECT_ACTIVITY + " WHERE a.description_embedding IS NULL";
        List<Activity> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                results.add(mapActivity(rs));
            }
        } catch (SQLException e) {
            LOG.error("Error finding activities without embedding", e);
        }
        return results;
    }

    public void updateEmbedding(Long id, float[] embedding) {
        String sql = "UPDATE activities SET description_embedding = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, embedding, OracleType.VECTOR);
            stmt.setLong(2, id);
            stmt.executeUpdate();
            LOG.debug("Updated embedding for activity id={}", id);
        } catch (SQLException e) {
            LOG.error("Error updating embedding for activity id={}", id, e);
        }
    }

    public Activity findById(Long id) {
        String sql = SELECT_ACTIVITY + " WHERE a.id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapActivity(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("Error finding activity by id={}", id, e);
        }
        return null;
    }

    private Activity mapActivity(ResultSet rs) throws SQLException {
        return new Activity(
            rs.getLong("id"),
            rs.getLong("destination_id"),
            rs.getString("destination_name"),
            rs.getString("name"),
            rs.getString("season"),
            rs.getString("description")
        );
    }
}
