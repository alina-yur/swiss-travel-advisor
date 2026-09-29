select d.name as destination,
       h.name as hotel,
       h.price_per_night
  from hotels h
  join destinations d
on d.id = h.destination_id
 where d.id = 3
   and h.price_per_night <= 200
 order by h.price_per_night;