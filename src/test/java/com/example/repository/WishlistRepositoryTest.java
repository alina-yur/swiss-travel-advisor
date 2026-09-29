package com.example.repository;

import com.example.entity.WishlistItemEntity;
import com.example.model.WishlistItem;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishlistRepositoryTest {

    @Test
    void saveDelegatesToGeneratedUpsert() {
        AtomicReference<WishlistItemEntity> saved = new AtomicReference<>();
        WishlistRepository repository = new WishlistRepository(null, saved::set);

        assertTrue(repository.save(
            "bda209a4-c8c6-4b75-9d42-44767be3ae9e",
            new WishlistItem("hotel", 42L)));

        assertEquals(new WishlistItemEntity(
            "bda209a4-c8c6-4b75-9d42-44767be3ae9e", "hotel", 42L), saved.get());
    }
}
