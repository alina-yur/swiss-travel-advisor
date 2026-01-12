-- Insert destinations (5-6 entries)
INSERT INTO destinations (name, region, description) VALUES
('Zermatt', 'Valais', 'A charming alpine village at the foot of the iconic Matterhorn, offering world-class skiing, hiking trails, and breathtaking mountain views. Car-free town with traditional Swiss chalets.');

INSERT INTO destinations (name, region, description) VALUES
('Interlaken', 'Bernese Oberland', 'Adventure capital of Switzerland nestled between Lake Thun and Lake Brienz, surrounded by the majestic Eiger, Mönch, and Jungfrau peaks. Perfect for paragliding, hiking, and mountain excursions.');

INSERT INTO destinations (name, region, description) VALUES
('Lucerne', 'Central Switzerland', 'Historic lakeside city famous for its medieval architecture, Chapel Bridge, and stunning lake views. Gateway to Mount Pilatus and Rigi, combining culture with natural beauty.');

INSERT INTO destinations (name, region, description) VALUES
('Lausanne', 'Vaud', 'Vibrant lakefront city on Lake Geneva, known for the Olympic Museum, medieval old town, and terraced Lavaux vineyards. Cultural hub with contemporary museums and festivals.');

INSERT INTO destinations (name, region, description) VALUES
('St. Moritz', 'Graubünden', 'Glamorous alpine resort town famous for luxury shopping, winter sports, and hosting the Winter Olympics twice. Crystal-clear mountain lakes and upscale dining experiences.');

INSERT INTO destinations (name, region, description) VALUES
('Lugano', 'Ticino', 'Mediterranean-style Swiss city with Italian flair, palm-lined promenades, and lakeside parks. Mild climate perfect for exploring Monte Brè, shopping, and enjoying aperitivo culture.');

-- Insert hotels (8-10 entries)
INSERT INTO hotels (destination_id, name, price_per_night, description) VALUES
(1, 'Matterhorn View Hotel', 280.00, 'Boutique hotel with panoramic Matterhorn views from every room. Features a spa, fine dining restaurant serving Swiss specialties, and ski-in access. Cozy fireplaces and alpine elegance.');

INSERT INTO hotels (destination_id, name, price_per_night, description) VALUES
(1, 'Alpine Lodge Zermatt', 150.00, 'Family-run chalet-style hotel offering warm hospitality and traditional Swiss breakfast. Walking distance to ski lifts and village center. Comfortable rooms with mountain views.');

INSERT INTO hotels (destination_id, name, price_per_night, description) VALUES
(2, 'Interlaken Grand Resort', 320.00, 'Luxury lakefront property with private beach access, infinity pool overlooking the mountains, and Michelin-starred restaurant. Spa with alpine wellness treatments.');

INSERT INTO hotels (destination_id, name, price_per_night, description) VALUES
(2, 'Adventure Base Hostel', 75.00, 'Modern hostel perfect for budget travelers and outdoor enthusiasts. Social atmosphere, communal kitchen, and organized adventure tours. Dorm and private room options.');

INSERT INTO hotels (destination_id, name, price_per_night, description) VALUES
(3, 'Lucerne Palace Hotel', 295.00, 'Historic Belle Époque hotel on the lakefront with elegant rooms, marble bathrooms, and classical Swiss service. Minutes from Chapel Bridge and old town attractions.');

INSERT INTO hotels (destination_id, name, price_per_night, description) VALUES
(3, 'Old Town Guesthouse', 120.00, 'Charming bed and breakfast in a renovated 16th-century building. Exposed wooden beams, antique furnishings, and homemade breakfast featuring local products.');

INSERT INTO hotels (destination_id, name, price_per_night, description) VALUES
(4, 'Lausanne Design Hotel', 210.00, 'Contemporary boutique hotel with modern art, rooftop terrace bar, and farm-to-table restaurant. Steps from the metro connecting to Olympic Museum and waterfront.');

INSERT INTO hotels (destination_id, name, price_per_night, description) VALUES
(5, 'St. Moritz Palace', 450.00, 'Iconic five-star hotel synonymous with luxury and elegance. Gourmet restaurants, champagne bar, spa with indoor pool, and impeccable service. Celebrity favorite since 1896.');

INSERT INTO hotels (destination_id, name, price_per_night, description) VALUES
(5, 'Mountain Comfort Inn', 180.00, 'Mid-range hotel offering great value with comfortable rooms, sauna, and hearty breakfast buffet. Easy access to ski slopes and hiking trails.');

INSERT INTO hotels (destination_id, name, price_per_night, description) VALUES
(6, 'Lugano Lakeside Retreat', 195.00, 'Modern hotel with Mediterranean-inspired design, rooftop pool, and Italian restaurant. Lake views, proximity to Parco Ciani, and vibrant nightlife nearby.');

-- Insert activities (5-6 entries)
INSERT INTO activities (destination_id, name, season, description) VALUES
(1, 'Gornergrat Railway Excursion', 'All Year', 'Scenic cogwheel train ride to 3,089m offering spectacular 360-degree views of the Matterhorn and 29 surrounding peaks. Observation platform, restaurant, and hiking trails at the summit.');

INSERT INTO activities (destination_id, name, season, description) VALUES
(2, 'Jungfraujoch - Top of Europe', 'All Year', 'Journey to Europe''s highest railway station at 3,454m. Ice Palace, Sphinx Observatory, and stunning views of the Aletsch Glacier. Alpine sensation and research exhibitions.');

INSERT INTO activities (destination_id, name, season, description) VALUES
(2, 'Paragliding Over Interlaken', 'Spring-Fall', 'Tandem paragliding flight over turquoise lakes and alpine valleys with professional pilots. No experience needed. Thrilling 20-30 minute flights with photo packages available.');

INSERT INTO activities (destination_id, name, season, description) VALUES
(3, 'Mount Pilatus Golden Round Trip', 'Spring-Fall', 'Combination journey via boat, cogwheel railway (world''s steepest), and aerial cableway. Summit activities include rope park, toboggan run, and panoramic restaurants.');

INSERT INTO activities (destination_id, name, season, description) VALUES
(4, 'Lavaux Vineyard Terraces Tour', 'Spring-Fall', 'UNESCO World Heritage wine region tour with tastings at family-run wineries. Walk through centuries-old terraced vineyards overlooking Lake Geneva with local Chasselas wines.');

INSERT INTO activities (destination_id, name, season, description) VALUES
(5, 'Bernina Express Scenic Train', 'All Year', 'UNESCO World Heritage railway journey through alpine passes, glaciers, and palm trees. From St. Moritz to Tirano, Italy, crossing 196 bridges and 55 tunnels in 4 hours.');
