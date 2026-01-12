package com.example.controller;

import com.example.model.WishlistItem;
import com.example.service.WishlistService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;

@Controller("/api")
public class WishlistController {
    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @Get("/wishlist")
    public List<WishlistItem> getWishlist() {
        return wishlistService.getWishlist();
    }
}
