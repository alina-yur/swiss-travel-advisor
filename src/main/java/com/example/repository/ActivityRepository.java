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
    private final DataSource dataSource;

    public ActivityRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Activity> searchByVector(float[] queryVector, Long destinationId, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT a.id, a.destination_id, d.name as destination_name,
                   a.name, a.season, a.description
            FROM activities a
            JOIN destinations d ON a.destination_id = d.id
            WHERE a.description_embedding IS NOT NULL
            """);

        List<Object> params = new ArrayList<>();

        if (destinationId != null) {
            sql.append(" AND a.destination_id = ?");
            params.add(destinationId);
        }

        sql.append(" ORDER BY VECTOR_DISTANCE(a.description_embedding, ?, COSINE) FETCH FIRST ? ROWS ONLY");
        params.add(queryVector);
        params.add(limit);

        List<Activity> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            for (Object param : params) {
                if (param instanceof Long) {
                    stmt.setLong(paramIndex++, (Long) param);
                } else if (param instanceof float[]) {
                    stmt.setObject(paramIndex++, param, OracleType.VECTOR);
                } else if (param instanceof Integer) {
                    stmt.setInt(paramIndex++, (Integer) param);
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new Activity(
                        rs.getLong("id"),
                        rs.getLong("destination_id"),
                        rs.getString("destination_name"),
                        rs.getString("name"),
                        rs.getString("season"),
                        rs.getString("description")
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error searching activities by vector", e);
        }
        return results;
    }

    public List<Activity> findAll() {
        String sql = """
            SELECT a.id, a.destination_id, d.name as destination_name,
                   a.name, a.season, a.description
            FROM activities a
            JOIN destinations d ON a.destination_id = d.id
            """;
        List<Activity> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                results.add(new Activity(
                    rs.getLong("id"),
                    rs.getLong("destination_id"),
                    rs.getString("destination_name"),
                    rs.getString("name"),
                    rs.getString("season"),
                    rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            LOG.error("Error finding all activities", e);
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
        String sql = """
            SELECT a.id, a.destination_id, d.name as destination_name,
                   a.name, a.season, a.description
            FROM activities a
            JOIN destinations d ON a.destination_id = d.id
            WHERE a.id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
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
        } catch (SQLException e) {
            LOG.error("Error finding activity by id={}", id, e);
        }
        return null;
    }
}
