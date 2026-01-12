package com.example.repository;

import com.example.model.Destination;
import jakarta.inject.Singleton;
import oracle.jdbc.OracleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class DestinationRepository {
    private static final Logger LOG = LoggerFactory.getLogger(DestinationRepository.class);
    private final DataSource dataSource;

    public DestinationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Destination> searchByVector(float[] queryVector, int limit) {
        String sql = """
            SELECT id, name, region, description
            FROM destinations
            WHERE description_embedding IS NOT NULL
            ORDER BY VECTOR_DISTANCE(description_embedding, ?, COSINE)
            FETCH FIRST ? ROWS ONLY
            """;

        List<Destination> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, queryVector, OracleType.VECTOR);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new Destination(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("region"),
                        rs.getString("description")
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error searching destinations by vector", e);
        }
        return results;
    }

    public List<Destination> findAll() {
        String sql = "SELECT id, name, region, description FROM destinations";
        List<Destination> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                results.add(new Destination(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("region"),
                    rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            LOG.error("Error finding all destinations", e);
        }
        return results;
    }

    public void updateEmbedding(Long id, float[] embedding) {
        String sql = "UPDATE destinations SET description_embedding = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, embedding, OracleType.VECTOR);
            stmt.setLong(2, id);
            stmt.executeUpdate();
            LOG.debug("Updated embedding for destination id={}", id);
        } catch (SQLException e) {
            LOG.error("Error updating embedding for destination id={}", id, e);
        }
    }
}
