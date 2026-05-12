-- Make `external_id` the canonical upsert key for `meals` and drop the
-- legacy `full_text` UNIQUE constraint.
--
-- The HTML scraper used `full_text` as the dedup key because no stable
-- upstream id was available. The new API gives us `<speise id>` which is
-- stable per recipe across days — but the title text can drift between
-- days (different allergen-code combos surface in the parenthetical),
-- so a bulk upsert over a 30-day window now sends multiple distinct
-- `full_text` values that all share one `external_id`. Without this
-- migration, those rows hit `meals_external_id_uidx` after the
-- ON CONFLICT (full_text) DO UPDATE path passes through them.

-- Drop both possible names the original constraint might have shipped under.
ALTER TABLE public.meals DROP CONSTRAINT IF EXISTS meals_full_text_key;
ALTER TABLE public.meals DROP CONSTRAINT IF EXISTS meals_full_text_unique;
DROP INDEX IF EXISTS public.meals_full_text_key;
DROP INDEX IF EXISTS public.meals_full_text_unique;
