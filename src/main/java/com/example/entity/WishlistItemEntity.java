package com.example.entity;

import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("wishlist_items")
public record WishlistItemEntity(
    String conversationId,

    String itemType,

    Long itemId
) {
}
