-- Add Oracle Spatial locations for location-aware travel search.
ALTER TABLE destinations ADD (location MDSYS.SDO_GEOMETRY);
ALTER TABLE hotels ADD (location MDSYS.SDO_GEOMETRY);
ALTER TABLE activities ADD (location MDSYS.SDO_GEOMETRY);

UPDATE destinations
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.7491, 46.0207, NULL), NULL, NULL)
WHERE name = 'Zermatt';

UPDATE destinations
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.8632, 46.6863, NULL), NULL, NULL)
WHERE name = 'Interlaken';

UPDATE destinations
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.3093, 47.0502, NULL), NULL, NULL)
WHERE name = 'Lucerne';

UPDATE destinations
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(6.6323, 46.5197, NULL), NULL, NULL)
WHERE name = 'Lausanne';

UPDATE destinations
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(9.8390, 46.4983, NULL), NULL, NULL)
WHERE name = 'St. Moritz';

UPDATE destinations
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.9511, 46.0037, NULL), NULL, NULL)
WHERE name = 'Lugano';

UPDATE hotels
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.7486, 46.0213, NULL), NULL, NULL)
WHERE name = 'Matterhorn View Hotel';

UPDATE hotels
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.7469, 46.0198, NULL), NULL, NULL)
WHERE name = 'Alpine Lodge Zermatt';

UPDATE hotels
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.8550, 46.6866, NULL), NULL, NULL)
WHERE name = 'Interlaken Grand Resort';

UPDATE hotels
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.8685, 46.6850, NULL), NULL, NULL)
WHERE name = 'Adventure Base Hostel';

UPDATE hotels
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.3110, 47.0521, NULL), NULL, NULL)
WHERE name = 'Lucerne Palace Hotel';

UPDATE hotels
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.3065, 47.0510, NULL), NULL, NULL)
WHERE name = 'Old Town Guesthouse';

UPDATE hotels
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(6.6336, 46.5210, NULL), NULL, NULL)
WHERE name = 'Lausanne Design Hotel';

UPDATE hotels
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(9.8381, 46.4970, NULL), NULL, NULL)
WHERE name = 'St. Moritz Palace';

UPDATE hotels
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(9.8348, 46.5010, NULL), NULL, NULL)
WHERE name = 'Mountain Comfort Inn';

UPDATE hotels
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.9535, 46.0048, NULL), NULL, NULL)
WHERE name = 'Lugano Lakeside Retreat';

UPDATE activities
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.7844, 45.9839, NULL), NULL, NULL)
WHERE name = 'Gornergrat Railway Excursion';

UPDATE activities
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.9850, 46.5475, NULL), NULL, NULL)
WHERE name = 'Jungfraujoch - Top of Europe';

UPDATE activities
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.8632, 46.6863, NULL), NULL, NULL)
WHERE name = 'Paragliding Over Interlaken';

UPDATE activities
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.2540, 46.9797, NULL), NULL, NULL)
WHERE name = 'Mount Pilatus Golden Round Trip';

UPDATE activities
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(6.7460, 46.4917, NULL), NULL, NULL)
WHERE name = 'Lavaux Vineyard Terraces Tour';

UPDATE activities
SET location = MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(9.8390, 46.4983, NULL), NULL, NULL)
WHERE name = 'Bernina Express Scenic Train';

DELETE FROM user_sdo_geom_metadata WHERE table_name IN ('DESTINATIONS', 'HOTELS', 'ACTIVITIES') AND column_name = 'LOCATION';

INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
VALUES (
    'DESTINATIONS',
    'LOCATION',
    MDSYS.SDO_DIM_ARRAY(
        MDSYS.SDO_DIM_ELEMENT('Longitude', -180, 180, 0.005),
        MDSYS.SDO_DIM_ELEMENT('Latitude', -90, 90, 0.005)
    ),
    4326
);

INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
VALUES (
    'HOTELS',
    'LOCATION',
    MDSYS.SDO_DIM_ARRAY(
        MDSYS.SDO_DIM_ELEMENT('Longitude', -180, 180, 0.005),
        MDSYS.SDO_DIM_ELEMENT('Latitude', -90, 90, 0.005)
    ),
    4326
);

INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
VALUES (
    'ACTIVITIES',
    'LOCATION',
    MDSYS.SDO_DIM_ARRAY(
        MDSYS.SDO_DIM_ELEMENT('Longitude', -180, 180, 0.005),
        MDSYS.SDO_DIM_ELEMENT('Latitude', -90, 90, 0.005)
    ),
    4326
);

CREATE INDEX idx_destinations_location ON destinations(location) INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
CREATE INDEX idx_hotels_location ON hotels(location) INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
CREATE INDEX idx_activities_location ON activities(location) INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
