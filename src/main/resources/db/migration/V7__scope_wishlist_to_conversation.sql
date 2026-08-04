DELETE FROM wishlist_items;

ALTER TABLE wishlist_items ADD (
    conversation_id VARCHAR2(36)
);

ALTER TABLE wishlist_items MODIFY (
    conversation_id NOT NULL
);

ALTER TABLE wishlist_items ADD CONSTRAINT uq_wishlist_conv_item
    UNIQUE (conversation_id, item_type, item_id);
