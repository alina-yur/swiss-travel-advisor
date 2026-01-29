-- Create destinations table with vector column
CREATE TABLE destinations (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR2(255) NOT NULL,
    region VARCHAR2(255) NOT NULL,
    description CLOB NOT NULL,
    description_embedding VECTOR(1536, FLOAT32)
);

-- Create hotels table with vector column
CREATE TABLE hotels (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    destination_id NUMBER NOT NULL,
    name VARCHAR2(255) NOT NULL,
    price_per_night NUMBER(10, 2) NOT NULL,
    description CLOB NOT NULL,
    description_embedding VECTOR(1536, FLOAT32),
    CONSTRAINT fk_hotel_destination FOREIGN KEY (destination_id) REFERENCES destinations(id)
);

-- Create activities table with vector column
CREATE TABLE activities (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    destination_id NUMBER NOT NULL,
    name VARCHAR2(255) NOT NULL,
    season VARCHAR2(50) NOT NULL,
    description CLOB NOT NULL,
    description_embedding VECTOR(1536, FLOAT32),
    CONSTRAINT fk_activity_destination FOREIGN KEY (destination_id) REFERENCES destinations(id)
);

-- Create wishlist_items table for storing user wishlists
CREATE TABLE wishlist_items (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_type VARCHAR2(50) NOT NULL,
    item_id NUMBER NOT NULL
);

-- Create indexes for better performance
CREATE INDEX idx_hotels_destination ON hotels(destination_id);
CREATE INDEX idx_activities_destination ON activities(destination_id);
CREATE INDEX idx_wishlist_item_type ON wishlist_items(item_type);
