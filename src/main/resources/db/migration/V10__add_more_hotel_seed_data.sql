-- Expand every seeded destination with a broader mix of hotel styles and prices.
-- Embeddings are intentionally left NULL; DataInitializer backfills them on startup.

-- Zermatt
INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Glacier Garden Chalet', 225.00,
       'Peaceful chalet hotel near the village edge with balconies facing the mountains, a small sauna, locally sourced breakfast, and convenient access to winter walking trails.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.7448, 46.0242, NULL), NULL, NULL)
FROM destinations WHERE name = 'Zermatt';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Gornergrat Explorer Inn', 135.00,
       'Practical alpine inn for hikers and skiers with secure equipment storage, an early breakfast, simple comfortable rooms, and a short walk to the Gornergrat railway.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.7505, 46.0230, NULL), NULL, NULL)
FROM destinations WHERE name = 'Zermatt';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Valais Spa House', 365.00,
       'Upscale wellness retreat featuring an indoor pool, herbal steam rooms, mountain-view suites, and a seasonal restaurant focused on produce from the Valais region.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.7477, 46.0174, NULL), NULL, NULL)
FROM destinations WHERE name = 'Zermatt';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'The Car-Free Corner', 105.00,
       'Friendly budget hotel on a quiet pedestrian lane with compact rooms, a shared lounge, complimentary electric-taxi pickup, and easy access to restaurants in the village center.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.7520, 46.0202, NULL), NULL, NULL)
FROM destinations WHERE name = 'Zermatt';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Matter Valley Family Suites', 260.00,
       'Spacious apartment-style suites with kitchenettes, a children''s playroom, laundry facilities, and family ski services close to the Sunnegga funicular.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.7513, 46.0227, NULL), NULL, NULL)
FROM destinations WHERE name = 'Zermatt';

-- Interlaken
INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Aare Riverside Hotel', 205.00,
       'Relaxed riverside hotel with garden terraces, bicycle rentals, modern rooms, and an easy walk to Interlaken Ost station and boats on Lake Brienz.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.8703, 46.6910, NULL), NULL, NULL)
FROM destinations WHERE name = 'Interlaken';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Harder Kulm Lodge', 165.00,
       'Comfortable lodge near the Harderbahn with forest views, a casual Swiss dining room, packed lunches for day trips, and rooms suited to couples and small families.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.8665, 46.6922, NULL), NULL, NULL)
FROM destinations WHERE name = 'Interlaken';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Jungfrau Panorama Suites', 355.00,
       'Premium suites with wide balconies overlooking the Jungfrau massif, an infinity-edge spa pool, concierge-planned excursions, and refined modern Swiss cuisine.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.8578, 46.6858, NULL), NULL, NULL)
FROM destinations WHERE name = 'Interlaken';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Backpackers Garden Rooms', 90.00,
       'Affordable private rooms and small dormitories around a leafy courtyard, with a guest kitchen, laundry, free local bus pass, and discounts on outdoor activities.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.8610, 46.6832, NULL), NULL, NULL)
FROM destinations WHERE name = 'Interlaken';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'West Station Family Hotel', 190.00,
       'Convenient family hotel near Interlaken West with connecting rooms, a play corner, generous breakfast buffet, and straightforward rail connections throughout the Bernese Oberland.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(7.8510, 46.6826, NULL), NULL, NULL)
FROM destinations WHERE name = 'Interlaken';

-- Lucerne
INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Reuss River House', 185.00,
       'Intimate riverside hotel in a restored townhouse with individually decorated rooms, a breakfast terrace, and views toward the old town walls and covered bridges.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.3038, 47.0524, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lucerne';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Pilatus View Residence', 245.00,
       'Contemporary serviced residence offering kitchenettes, long-stay amenities, mountain-facing balconies, and quick bus connections to the station and Kriens cableway.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.3007, 47.0465, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lucerne';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Chapel Bridge Budget Hotel', 110.00,
       'Simple central hotel for value-minded visitors with spotless compact rooms, self check-in, luggage lockers, and major old town sights just outside the door.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.3071, 47.0515, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lucerne';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Lake Lucerne Wellness Retreat', 390.00,
       'Elegant lakefront retreat with private bathing access, panoramic spa facilities, quiet rooms, and tasting menus inspired by Central Swiss farms and fisheries.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.3195, 47.0520, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lucerne';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Musegg Family Inn', 155.00,
       'Welcoming inn below the Musegg Wall with family rooms, board games, a shaded courtyard, and an all-day cafe serving light regional dishes.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.3052, 47.0557, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lucerne';

-- Lausanne
INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Ouchy Marina Hotel', 255.00,
       'Polished waterfront hotel near the Ouchy promenade with lake-view rooms, complimentary bicycles, a seafood brasserie, and direct metro access to the city center.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(6.6294, 46.5070, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lausanne';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Cathedral Quarter Rooms', 140.00,
       'Small heritage guesthouse on a steep old town street with characterful rooms, city rooftop views, and cafes, museums, and the cathedral within a short walk.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(6.6352, 46.5237, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lausanne';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Lavaux Gateway Suites', 275.00,
       'Apartment suites east of the center with kitchen facilities, lake-facing balconies, wine-tasting partnerships, and easy train access to the Lavaux vineyard villages.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(6.6480, 46.5150, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lausanne';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Flon Urban Stay', 170.00,
       'Energetic design-led hotel in the Flon district with coworking tables, late breakfast, soundproof rooms, and nightlife, shopping, and metro connections nearby.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(6.6290, 46.5208, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lausanne';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Olympic Park Guesthouse', 115.00,
       'Unpretentious guesthouse between the station and lakeshore offering bright rooms, secure bicycle storage, continental breakfast, and convenient access to the Olympic Museum.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(6.6372, 46.5118, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lausanne';

-- St. Moritz
INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Engadin Lake Lodge', 235.00,
       'Warm timber-lined lodge near the lakeshore with cross-country ski storage, hearty Engadin breakfasts, a fireside lounge, and relaxed rooms overlooking the valley.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(9.8418, 46.4938, NULL), NULL, NULL)
FROM destinations WHERE name = 'St. Moritz';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Corviglia Ski House', 310.00,
       'Slope-focused hotel close to the mountain railway with ski concierge service, a recovery sauna, generous half-board dining, and contemporary alpine rooms.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(9.8370, 46.5007, NULL), NULL, NULL)
FROM destinations WHERE name = 'St. Moritz';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Bad Thermal Residence', 285.00,
       'Quiet wellness residence in St. Moritz Bad with thermal-inspired treatments, indoor pool access, spacious studios, and nearby trails around the lake.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(9.8330, 46.4850, NULL), NULL, NULL)
FROM destinations WHERE name = 'St. Moritz';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Upper Engadin Hostel', 125.00,
       'Smart budget base with private and shared rooms, a communal kitchen, waxing bench for skis, and local transport links to slopes and neighboring Engadin villages.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(9.8460, 46.4972, NULL), NULL, NULL)
FROM destinations WHERE name = 'St. Moritz';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Via Serlas Grand Suites', 520.00,
       'Discreet luxury suites near Via Serlas featuring private balconies, butler-style service, a destination restaurant, chauffeur transfers, and a serene adults-only spa.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(9.8396, 46.4992, NULL), NULL, NULL)
FROM destinations WHERE name = 'St. Moritz';

-- Lugano
INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Parco Ciani Hotel', 220.00,
       'Leafy city hotel beside Parco Ciani with lake glimpses, shaded breakfast terraces, complimentary bicycles, and a short walk to the historic center.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.9581, 46.0061, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lugano';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Monte Bre Panorama Inn', 180.00,
       'Hillside inn with broad lake views, a regional Ticinese restaurant, sunny terraces, and easy access to the Monte Bre funicular and walking paths.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.9730, 46.0115, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lugano';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Ticino Courtyard Rooms', 125.00,
       'Affordable rooms arranged around a colorful courtyard near the old town, offering espresso breakfast, air conditioning, and friendly local dining recommendations.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.9500, 46.0047, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lugano';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Lugano Art and Design Suites', 295.00,
       'Stylish all-suite hotel showcasing contemporary Swiss-Italian art, with kitchenettes, a rooftop aperitivo bar, and convenient access to the LAC cultural center.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.9470, 46.0008, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lugano';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'San Salvatore Family Resort', 250.00,
       'Family-oriented resort toward Paradiso with connecting rooms, outdoor pool, supervised play space, and nearby funicular rides up Monte San Salvatore.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.9452, 45.9918, NULL), NULL, NULL)
FROM destinations WHERE name = 'Lugano';

-- Zurich
INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Limmat Riverside Hotel', 230.00,
       'Modern riverside hotel with quiet soundproof rooms, a waterside breakfast room, bicycle rentals, and tram connections to the main station and lake.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.5405, 47.3790, NULL), NULL, NULL)
FROM destinations WHERE name = 'Zurich';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Langstrasse Social Stay', 145.00,
       'Lively affordable hotel with compact rooms, communal workspaces, late check-in, and a neighborhood full of international restaurants, bars, and music venues.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.5277, 47.3785, NULL), NULL, NULL)
FROM destinations WHERE name = 'Zurich';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Uetliberg View Suites', 310.00,
       'Spacious residential-style suites in a calm hillside neighborhood with kitchenettes, city-view balconies, fitness facilities, and direct rail access to central Zurich.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.5138, 47.3640, NULL), NULL, NULL)
FROM destinations WHERE name = 'Zurich';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Enge Business Hotel', 205.00,
       'Efficient business hotel near Bahnhof Enge with ergonomic work areas, early breakfast, meeting rooms, and quick connections to the financial district and airport.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.5308, 47.3643, NULL), NULL, NULL)
FROM destinations WHERE name = 'Zurich';

INSERT INTO hotels (destination_id, name, price_per_night, description, location)
SELECT id, 'Seefeld Garden Residence', 275.00,
       'Calm boutique residence in Seefeld with garden-facing rooms, wellness studio, seasonal breakfast, and nearby lake swimming areas, opera, and tram stops.',
       MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(8.5588, 47.3598, NULL), NULL, NULL)
FROM destinations WHERE name = 'Zurich';
