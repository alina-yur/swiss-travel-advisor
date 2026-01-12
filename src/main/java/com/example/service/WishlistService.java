package com.example.service;

import com.example.model.WishlistItem;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class WishlistService {
    private final List<WishlistItem> wishlist = Collections.synchronizedList(new ArrayList<>());

    public void addItem(WishlistItem item) {
        wishlist.add(item);
    }

    public List<WishlistItem> getWishlist() {
        return new ArrayList<>(wishlist);
    }

    public void clearWishlist() {
        wishlist.clear();
    }
}
