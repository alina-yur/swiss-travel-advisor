-- Keep the oldest row for each seeded catalog item. Development databases may
-- contain duplicates from manually replaying seed inserts before Flyway owned
-- the complete schema.

-- First collapse duplicate destinations and preserve wishlist references.
DELETE FROM wishlist_items w
WHERE w.item_type = 'destination'
  AND EXISTS (
      SELECT 1
      FROM destinations d
      WHERE d.id = w.item_id
        AND d.id <> (SELECT MIN(d2.id) FROM destinations d2 WHERE d2.name = d.name)
  )
  AND EXISTS (
      SELECT 1
      FROM wishlist_items existing
      WHERE existing.conversation_id = w.conversation_id
        AND existing.item_type = w.item_type
        AND existing.item_id = (
            SELECT MIN(d2.id)
            FROM destinations d
            JOIN destinations d2 ON d2.name = d.name
            WHERE d.id = w.item_id
        )
  );

UPDATE wishlist_items w
SET w.item_id = (
    SELECT MIN(d2.id)
    FROM destinations d
    JOIN destinations d2 ON d2.name = d.name
    WHERE d.id = w.item_id
)
WHERE w.item_type = 'destination'
  AND EXISTS (
      SELECT 1
      FROM destinations d
      WHERE d.id = w.item_id
        AND d.id <> (SELECT MIN(d2.id) FROM destinations d2 WHERE d2.name = d.name)
  );

UPDATE hotels h
SET h.destination_id = (
    SELECT MIN(d2.id)
    FROM destinations d
    JOIN destinations d2 ON d2.name = d.name
    WHERE d.id = h.destination_id
)
WHERE EXISTS (
    SELECT 1
    FROM destinations d
    WHERE d.id = h.destination_id
      AND d.id <> (SELECT MIN(d2.id) FROM destinations d2 WHERE d2.name = d.name)
);

UPDATE activities a
SET a.destination_id = (
    SELECT MIN(d2.id)
    FROM destinations d
    JOIN destinations d2 ON d2.name = d.name
    WHERE d.id = a.destination_id
)
WHERE EXISTS (
    SELECT 1
    FROM destinations d
    WHERE d.id = a.destination_id
      AND d.id <> (SELECT MIN(d2.id) FROM destinations d2 WHERE d2.name = d.name)
);

DELETE FROM destinations d
WHERE d.id <> (SELECT MIN(d2.id) FROM destinations d2 WHERE d2.name = d.name);

-- Then collapse hotels after all destination references use their canonical ID.
DELETE FROM wishlist_items w
WHERE w.item_type = 'hotel'
  AND EXISTS (
      SELECT 1
      FROM hotels h
      WHERE h.id = w.item_id
        AND h.id <> (
            SELECT MIN(h2.id)
            FROM hotels h2
            WHERE h2.destination_id = h.destination_id
              AND h2.name = h.name
        )
  )
  AND EXISTS (
      SELECT 1
      FROM wishlist_items existing
      WHERE existing.conversation_id = w.conversation_id
        AND existing.item_type = w.item_type
        AND existing.item_id = (
            SELECT MIN(h2.id)
            FROM hotels h
            JOIN hotels h2
              ON h2.destination_id = h.destination_id
             AND h2.name = h.name
            WHERE h.id = w.item_id
        )
  );

UPDATE wishlist_items w
SET w.item_id = (
    SELECT MIN(h2.id)
    FROM hotels h
    JOIN hotels h2
      ON h2.destination_id = h.destination_id
     AND h2.name = h.name
    WHERE h.id = w.item_id
)
WHERE w.item_type = 'hotel'
  AND EXISTS (
      SELECT 1
      FROM hotels h
      WHERE h.id = w.item_id
        AND h.id <> (
            SELECT MIN(h2.id)
            FROM hotels h2
            WHERE h2.destination_id = h.destination_id
              AND h2.name = h.name
        )
  );

DELETE FROM hotels h
WHERE h.id <> (
    SELECT MIN(h2.id)
    FROM hotels h2
    WHERE h2.destination_id = h.destination_id
      AND h2.name = h.name
);

-- Apply the same canonicalization to activities.
DELETE FROM wishlist_items w
WHERE w.item_type = 'activity'
  AND EXISTS (
      SELECT 1
      FROM activities a
      WHERE a.id = w.item_id
        AND a.id <> (
            SELECT MIN(a2.id)
            FROM activities a2
            WHERE a2.destination_id = a.destination_id
              AND a2.name = a.name
        )
  )
  AND EXISTS (
      SELECT 1
      FROM wishlist_items existing
      WHERE existing.conversation_id = w.conversation_id
        AND existing.item_type = w.item_type
        AND existing.item_id = (
            SELECT MIN(a2.id)
            FROM activities a
            JOIN activities a2
              ON a2.destination_id = a.destination_id
             AND a2.name = a.name
            WHERE a.id = w.item_id
        )
  );

UPDATE wishlist_items w
SET w.item_id = (
    SELECT MIN(a2.id)
    FROM activities a
    JOIN activities a2
      ON a2.destination_id = a.destination_id
     AND a2.name = a.name
    WHERE a.id = w.item_id
)
WHERE w.item_type = 'activity'
  AND EXISTS (
      SELECT 1
      FROM activities a
      WHERE a.id = w.item_id
        AND a.id <> (
            SELECT MIN(a2.id)
            FROM activities a2
            WHERE a2.destination_id = a.destination_id
              AND a2.name = a.name
        )
  );

DELETE FROM activities a
WHERE a.id <> (
    SELECT MIN(a2.id)
    FROM activities a2
    WHERE a2.destination_id = a.destination_id
      AND a2.name = a.name
);

-- Make future duplicate seed or manual inserts fail instead of polluting search.
ALTER TABLE destinations ADD CONSTRAINT uq_destinations_name UNIQUE (name);
ALTER TABLE hotels ADD CONSTRAINT uq_hotels_dest_name UNIQUE (destination_id, name);
ALTER TABLE activities ADD CONSTRAINT uq_activities_dest_name UNIQUE (destination_id, name);
