package com.example.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record WishlistItem(Long id, String itemType, Long itemId) {

    public WishlistItem(String itemType, Long itemId) {
        this(null, itemType, itemId);
    }
}
