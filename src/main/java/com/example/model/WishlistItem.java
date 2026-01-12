package com.example.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record WishlistItem(
    String itemType,
    Long itemId,
    String name,
    String description
) {}
