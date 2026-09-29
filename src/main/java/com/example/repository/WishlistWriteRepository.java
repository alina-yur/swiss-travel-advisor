package com.example.repository;

import com.example.entity.WishlistItemEntity;
import io.micronaut.data.annotation.Upsert;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface WishlistWriteRepository {

    @Upsert(conflictsOn = {"conversationId", "itemType", "itemId"})
    void upsert(WishlistItemEntity item);
}
