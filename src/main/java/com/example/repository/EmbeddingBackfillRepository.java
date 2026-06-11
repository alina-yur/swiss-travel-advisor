package com.example.repository;

import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;
import oracle.jdbc.OracleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Connectable
public class EmbeddingBackfillRepository {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingBackfillRepository.class);

    private final DataSource dataSource;

    public EmbeddingBackfillRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<DestinationEmbeddingSeed> findDestinationsWithoutEmbedding() {
        String sql = "SELECT id, name, region, description FROM destinations WHERE description_embedding IS NULL";
        List<DestinationEmbeddingSeed> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(new DestinationEmbeddingSeed(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("region"),
                    rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            LOG.error("Error finding destinations without embeddings", e);
        }
        return results;
    }

    public List<HotelEmbeddingSeed> findHotelsWithoutEmbedding() {
        String sql = """
            SELECT h.id, h.name, h.description, d.name AS destination_name
            FROM hotels h
            JOIN destinations d ON h.destination_id = d.id
            WHERE h.description_embedding IS NULL
            """;
        List<HotelEmbeddingSeed> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(new HotelEmbeddingSeed(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("destination_name"),
                    rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            LOG.error("Error finding hotels without embeddings", e);
        }
        return results;
    }

    public List<ActivityEmbeddingSeed> findActivitiesWithoutEmbedding() {
        String sql = """
            SELECT a.id, a.name, a.season, a.description, d.name AS destination_name
            FROM activities a
            JOIN destinations d ON a.destination_id = d.id
            WHERE a.description_embedding IS NULL
            """;
        List<ActivityEmbeddingSeed> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(new ActivityEmbeddingSeed(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("destination_name"),
                    rs.getString("season"),
                    rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            LOG.error("Error finding activities without embeddings", e);
        }
        return results;
    }

    public void updateDestinationEmbedding(Long id, float[] embedding) {
        updateEmbedding("destinations", id, embedding);
    }

    public void updateHotelEmbedding(Long id, float[] embedding) {
        updateEmbedding("hotels", id, embedding);
    }

    public void updateActivityEmbedding(Long id, float[] embedding) {
        updateEmbedding("activities", id, embedding);
    }

    private void updateEmbedding(String tableName, Long id, float[] embedding) {
        String sql = "UPDATE " + tableName + " SET description_embedding = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, embedding, OracleType.VECTOR);
            stmt.setLong(2, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error updating embedding for {} id={}", tableName, id, e);
        }
    }

    public record DestinationEmbeddingSeed(Long id, String name, String region, String description) {
    }

    public record HotelEmbeddingSeed(Long id, String name, String destinationName, String description) {
    }

    public record ActivityEmbeddingSeed(Long id, String name, String destinationName, String season, String description) {
    }
}
