package com.example.repository;

import com.example.model.Hotel;
import jakarta.inject.Singleton;
import oracle.jdbc.OracleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class HotelRepository {
    private static final Logger LOG = LoggerFactory.getLogger(HotelRepository.class);
    private static final String SELECT_HOTEL = """
        SELECT h.id, h.destination_id, d.name as destination_name,
               h.name, h.price_per_night, h.description
        FROM hotels h
        JOIN destinations d ON h.destination_id = d.id
        """;

    private final DataSource dataSource;

    public HotelRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Hotel> searchByVector(float[] queryVector, Long destinationId, Double maxPrice, int limit) {
        String sql = SELECT_HOTEL + """
            WHERE h.description_embedding IS NOT NULL
            """ + (destinationId != null ? " AND h.destination_id = ?" : "")
            + (maxPrice != null ? " AND h.price_per_night <= ?" : "") + """
             ORDER BY VECTOR_DISTANCE(h.description_embedding, ?, COSINE) FETCH FIRST ? ROWS ONLY
            """;

        List<Hotel> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int paramIndex = 1;
            if (destinationId != null) {
                stmt.setLong(paramIndex++, destinationId);
            }
            if (maxPrice != null) {
                stmt.setDouble(paramIndex++, maxPrice);
            }
            stmt.setObject(paramIndex++, queryVector, OracleType.VECTOR);
            stmt.setInt(paramIndex, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapHotel(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error searching hotels by vector", e);
        }
        return results;
    }

    public List<Hotel> findAll() {
        List<Hotel> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_HOTEL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                results.add(mapHotel(rs));
            }
        } catch (SQLException e) {
            LOG.error("Error finding all hotels", e);
        }
        return results;
    }

    public List<Hotel> findWithoutEmbedding() {
        String sql = SELECT_HOTEL + " WHERE h.description_embedding IS NULL";
        List<Hotel> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                results.add(mapHotel(rs));
            }
        } catch (SQLException e) {
            LOG.error("Error finding hotels without embedding", e);
        }
        return results;
    }

    public void updateEmbedding(Long id, float[] embedding) {
        String sql = "UPDATE hotels SET description_embedding = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, embedding, OracleType.VECTOR);
            stmt.setLong(2, id);
            stmt.executeUpdate();
            LOG.debug("Updated embedding for hotel id={}", id);
        } catch (SQLException e) {
            LOG.error("Error updating embedding for hotel id={}", id, e);
        }
    }

    public Hotel findById(Long id) {
        String sql = SELECT_HOTEL + " WHERE h.id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapHotel(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("Error finding hotel by id={}", id, e);
        }
        return null;
    }

    private Hotel mapHotel(ResultSet rs) throws SQLException {
        return new Hotel(
            rs.getLong("id"),
            rs.getLong("destination_id"),
            rs.getString("destination_name"),
            rs.getString("name"),
            rs.getDouble("price_per_night"),
            rs.getString("description")
        );
    }
}
