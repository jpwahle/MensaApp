-- Migration: switch data source from HTML scraping to the official
-- Studierendenwerk Göttingen mobile-app API. Adds richer per-dish fields,
-- per-dish prices on `meal_dates` (replacing fuzzy `canteen_prices`
-- lookup), DB-sourced opening hours, and live occupancy snapshots.
-- All additions are nullable / new tables; existing rows stay valid.

-- ───── meals: richer per-dish fields from /api/mensaplanAll ─────
ALTER TABLE public.meals
  ADD COLUMN IF NOT EXISTS external_id    int,
  ADD COLUMN IF NOT EXISTS title_en       text,
  ADD COLUMN IF NOT EXISTS description_en text,
  ADD COLUMN IF NOT EXISTS sides_en       text[],
  ADD COLUMN IF NOT EXISTS rating_avg     real,
  ADD COLUMN IF NOT EXISTS recipe_name    text;

CREATE UNIQUE INDEX IF NOT EXISTS meals_external_id_uidx
  ON public.meals (external_id)
  WHERE external_id IS NOT NULL;

-- ───── canteens: external id + slug for stable lookups ─────
ALTER TABLE public.canteens
  ADD COLUMN IF NOT EXISTS external_id int,
  ADD COLUMN IF NOT EXISTS slug        text;

CREATE UNIQUE INDEX IF NOT EXISTS canteens_external_id_uidx
  ON public.canteens (external_id)
  WHERE external_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS canteens_slug_uidx
  ON public.canteens (slug)
  WHERE slug IS NOT NULL;

-- Backfill the four mensas the new scraper recognises. INSERT first so
-- the rows exist before we attach external IDs/slugs; harmless if they
-- already exist (UNIQUE on name keeps it idempotent).
INSERT INTO public.canteens (name)
VALUES
  ('Zentralmensa'),
  ('Mensa am Turm'),
  ('CGiN'),
  ('Bistro HAWK')
ON CONFLICT (name) DO NOTHING;

UPDATE public.canteens SET external_id = 4014, slug = 'zentral' WHERE name = 'Zentralmensa';
UPDATE public.canteens SET external_id = 4155, slug = 'turm'    WHERE name = 'Mensa am Turm';
UPDATE public.canteens SET external_id = 4209, slug = 'cgin'    WHERE name = 'CGiN';
UPDATE public.canteens SET external_id = 4272, slug = 'hawk'    WHERE name = 'Bistro HAWK';

-- ───── meal_dates: per-dish prices ─────
-- API gives prices on the dish itself; this collapses the canteen_prices
-- fuzzy match into a direct read.
ALTER TABLE public.meal_dates
  ADD COLUMN IF NOT EXISTS price_students        text,
  ADD COLUMN IF NOT EXISTS price_employees       text,
  ADD COLUMN IF NOT EXISTS price_guests          text,
  ADD COLUMN IF NOT EXISTS price_students_cents  int,
  ADD COLUMN IF NOT EXISTS price_employees_cents int,
  ADD COLUMN IF NOT EXISTS price_guests_cents    int;

-- ───── canteen_hours: weekly schedule from /api/oeffnungszeiten ─────
CREATE TABLE IF NOT EXISTS public.canteen_hours (
  id          uuid       PRIMARY KEY DEFAULT gen_random_uuid(),
  canteen_id  uuid       NOT NULL REFERENCES public.canteens(id) ON DELETE CASCADE,
  day_of_week smallint   NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- ISO Mon=1
  open_time   time,                                                     -- null = closed
  close_time  time,
  updated_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (canteen_id, day_of_week)
);

ALTER TABLE public.canteen_hours ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Enable read access for all users"
  ON public.canteen_hours FOR SELECT USING (true);

-- ───── canteen_occupancy: time-series from /api/frequenz ─────
CREATE TABLE IF NOT EXISTS public.canteen_occupancy (
  id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  canteen_id        uuid        NOT NULL REFERENCES public.canteens(id) ON DELETE CASCADE,
  observed_at       timestamptz NOT NULL DEFAULT now(),
  sales_current     numeric,
  sales_avg_weekday numeric,
  sales_avg_yearly  numeric,
  color             text,
  status_key        text
);

CREATE INDEX IF NOT EXISTS canteen_occupancy_recent_idx
  ON public.canteen_occupancy (canteen_id, observed_at DESC);

ALTER TABLE public.canteen_occupancy ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Enable read access for all users"
  ON public.canteen_occupancy FOR SELECT USING (true);

-- Latest snapshot per canteen — what the UI reads.
CREATE OR REPLACE VIEW public.canteen_occupancy_latest AS
  SELECT DISTINCT ON (canteen_id) *
  FROM public.canteen_occupancy
  ORDER BY canteen_id, observed_at DESC;

-- View security: inherit RLS from the underlying table (Supabase default).
GRANT SELECT ON public.canteen_occupancy_latest TO anon, authenticated;
