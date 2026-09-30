package com.example.controller;

import com.example.model.WishlistItem;
import com.example.repository.WishlistRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;

@Controller("/api")
@ExecuteOn(TaskExecutors.BLOCKING)
public class WishlistController {
    private final WishlistRepository wishlistRepository;

    public WishlistController(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    @Get("/wishlist")
    public List<WishlistItem> getWishlist(@Nullable @QueryValue String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        return wishlistRepository.findAll(conversationId);
    }
}
