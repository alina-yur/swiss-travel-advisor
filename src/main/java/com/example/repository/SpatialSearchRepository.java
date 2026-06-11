package com.example.repository;

import com.example.entity.ActivityEntity;
import com.example.entity.DestinationEntity;
import com.example.entity.HotelEntity;
import io.micronaut.data.connection.annotation.Connectable;
import io.micronaut.data.model.vector.Vector;
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
public class SpatialSearchRepository {
    private static final Logger LOG = LoggerFactory.getLogger(SpatialSearchRepository.class);

    private final DataSource dataSource;

    public SpatialSearchRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<DestinationEntity> searchDestinationsByVectorNear(Vector embedding, double longitude, double latitude, double radiusKm) {
        String sql = """
            SELECT id, name, region, description
            FROM destinations
            WHERE description_embedding IS NOT NULL
              AND location IS NOT NULL
              AND SDO_WITHIN_DISTANCE(
                    location,
                    MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(?, ?, NULL), NULL, NULL),
                    'distance=' || ? || ' unit=KM'
                  ) = 'TRUE'
            ORDER BY VECTOR_DISTANCE(description_embedding, ?, COSINE)
            FETCH FIRST 5 ROWS ONLY
            """;

        List<DestinationEntity> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindLocationAndVector(stmt, embedding, longitude, latitude, radiusKm, 1);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new DestinationEntity(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("region"),
                        rs.getString("description"),
                        null,
                        null
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error searching destinations by vector near location", e);
        }
        return results;
    }

    public List<HotelEntity> searchHotelsByVectorNear(
        Vector embedding,
        double longitude,
        double latitude,
        double radiusKm,
        Double maxPrice
    ) {
        String sql = """
            SELECT id, destination_id, name, price_per_night, description
            FROM hotels
            WHERE description_embedding IS NOT NULL
              AND location IS NOT NULL
            """ + (maxPrice == null ? "" : "  AND price_per_night <= ?\n") + """
              AND SDO_WITHIN_DISTANCE(
                    location,
                    MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(?, ?, NULL), NULL, NULL),
                    'distance=' || ? || ' unit=KM'
                  ) = 'TRUE'
            ORDER BY VECTOR_DISTANCE(description_embedding, ?, COSINE)
            FETCH FIRST 5 ROWS ONLY
            """;

        List<HotelEntity> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int parameterIndex = 1;
            if (maxPrice != null) {
                stmt.setDouble(parameterIndex++, maxPrice);
            }
            bindLocationAndVector(stmt, embedding, longitude, latitude, radiusKm, parameterIndex);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new HotelEntity(
                        rs.getLong("id"),
                        rs.getLong("destination_id"),
                        rs.getString("name"),
                        rs.getDouble("price_per_night"),
                        rs.getString("description"),
                        null,
                        null
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error searching hotels by vector near location", e);
        }
        return results;
    }

    public List<ActivityEntity> searchActivitiesByVectorNear(Vector embedding, double longitude, double latitude, double radiusKm) {
        String sql = """
            SELECT id, destination_id, name, season, description
            FROM activities
            WHERE description_embedding IS NOT NULL
              AND location IS NOT NULL
              AND SDO_WITHIN_DISTANCE(
                    location,
                    MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(?, ?, NULL), NULL, NULL),
                    'distance=' || ? || ' unit=KM'
                  ) = 'TRUE'
            ORDER BY VECTOR_DISTANCE(description_embedding, ?, COSINE)
            FETCH FIRST 5 ROWS ONLY
            """;

        List<ActivityEntity> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindLocationAndVector(stmt, embedding, longitude, latitude, radiusKm, 1);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new ActivityEntity(
                        rs.getLong("id"),
                        rs.getLong("destination_id"),
                        rs.getString("name"),
                        rs.getString("season"),
                        rs.getString("description"),
                        null,
                        null
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error searching activities by vector near location", e);
        }
        return results;
    }

    private void bindLocationAndVector(
        PreparedStatement stmt,
        Vector embedding,
        double longitude,
        double latitude,
        double radiusKm,
        int startIndex
    ) throws SQLException {
        int parameterIndex = startIndex;
        stmt.setDouble(parameterIndex++, longitude);
        stmt.setDouble(parameterIndex++, latitude);
        stmt.setDouble(parameterIndex++, radiusKm);
        stmt.setObject(parameterIndex, embedding.toFloatArray(), OracleType.VECTOR);
    }
}
