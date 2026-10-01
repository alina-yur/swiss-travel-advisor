SELECT d.name AS destination,
       h.name AS hotel,
       h.price_per_night
FROM hotels h
JOIN destinations d
  ON d.id = h.destination_id
WHERE d.name = 'Lucerne'
  AND h.price_per_night <= 200
ORDER BY h.price_per_night;
