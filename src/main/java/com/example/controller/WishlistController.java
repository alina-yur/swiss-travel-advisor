package com.example.controller;

import com.example.model.WishlistItem;
import com.example.repository.WishlistRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;

@Controller("/api")
public class WishlistController {
    private final WishlistRepository wishlistRepository;

    public WishlistController(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    @Get("/wishlist")
    public List<WishlistItem> getWishlist() {
        return wishlistRepository.findAll();
    }
}
