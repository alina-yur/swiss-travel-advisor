-- Add Zurich as a supported location-aware search anchor.
INSERT INTO destinations (name, region, description, location) VALUES (
    'Zurich',
    'Zurich',
    'Switzerland''s largest city, set on Lake Zurich with a walkable old town, strong museum scene, lakeside promenades, shopping streets, and quick access to Uetliberg for city and alpine views.',
    MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.5417, 47.3769, NULL), NULL, NULL)
);

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id,
       'Zurich Old Town Boutique',
       240.00,
       'Quiet boutique hotel in Zurich''s old town with compact modern rooms, easy tram access, and walking distance to the Limmat river, Bahnhofstrasse, and lakeside promenades.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.5437, 47.3728, NULL), NULL, NULL)
FROM destinations
WHERE name = 'Zurich';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id,
       'Lake Zurich Guesthouse',
       185.00,
       'Relaxed guesthouse near the lake with simple bright rooms, breakfast, and quick access to boat piers, swimming spots, and evening walks along the waterfront.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.5484, 47.3603, NULL), NULL, NULL)
FROM destinations
WHERE name = 'Zurich';

INSERT INTO activities (destination_id, name, season, description, location)
SELECT id,
       'Lake Zurich Promenade and Boat Cruise',
       'All Year',
       'Easy lakeside walk with optional boat cruise from Burkliplatz. Good for relaxed views, cafes, swimming areas in summer, and sunset over the lake and surrounding hills.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.5411, 47.3660, NULL), NULL, NULL)
FROM destinations
WHERE name = 'Zurich';

INSERT INTO activities (destination_id, name, season, description, location)
SELECT id,
       'Uetliberg Panorama Hike',
       'All Year',
       'Short train ride and ridge walk above Zurich with broad views over the city, Lake Zurich, and the Alps on clear days. Popular for sunrise, sunset, and casual hiking.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.4910, 47.3495, NULL), NULL, NULL)
FROM destinations
WHERE name = 'Zurich';

INSERT INTO activities (destination_id, name, season, description, location)
SELECT id,
       'Kunsthaus and Old Town Walk',
       'All Year',
       'City culture route combining the Kunsthaus art museum, Grossmunster, narrow old town lanes, riverside viewpoints, cafes, and independent shops.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.5480, 47.3702, NULL), NULL, NULL)
FROM destinations
WHERE name = 'Zurich';
