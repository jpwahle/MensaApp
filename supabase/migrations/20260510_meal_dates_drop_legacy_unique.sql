-- Drop the legacy UNIQUE constraint on (meal_id, canteen_id, served_on).
--
-- This was added by the original HTML-scraper schema and enforced "the
-- same dish can appear at most once per (canteen, date)". With the new
-- API the upstream legitimately lists the same `<speise>` under multiple
-- categories on the same day (e.g. a salad bowl available under both
-- "Menü 1" and "Salatbuffet"). That makes the constraint produce 23505
-- on every bulk upsert, even though (canteen_id, served_on, category) —
-- the upsert's actual conflict target — is fine.
--
-- After the drop the only meaningful uniqueness on meal_dates is
-- (canteen_id, served_on, category), which is what the scraper already
-- targets with ON CONFLICT.

ALTER TABLE public.meal_dates
  DROP CONSTRAINT IF EXISTS meal_dates_meal_id_canteen_id_served_on_key;

DROP INDEX IF EXISTS public.meal_dates_meal_id_canteen_id_served_on_key;
