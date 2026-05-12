-- Convert the partial unique INDEX on meals.external_id into a proper
-- UNIQUE CONSTRAINT so PostgREST's `onConflict=external_id` matches.
--
-- The original (20260505_api_migration.sql) created:
--   CREATE UNIQUE INDEX meals_external_id_uidx
--     ON meals (external_id) WHERE external_id IS NOT NULL;
--
-- That partial index correctly prevents duplicate non-null IDs, but
-- PostgreSQL's ON CONFLICT clause requires the conflict target to either
-- be a full unique constraint, or a unique index where the predicate is
-- replayed in the ON CONFLICT spec. supabase-js can't add a WHERE clause
-- to its `onConflict` argument, so the scraper hits 42P10
-- ("no unique or exclusion constraint matching the ON CONFLICT specification").
--
-- A non-partial UNIQUE CONSTRAINT works for our case because PostgreSQL
-- treats NULL values as distinct in UNIQUE constraints by default —
-- multiple legacy rows with `external_id IS NULL` coexist fine.

DROP INDEX IF EXISTS public.meals_external_id_uidx;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'public.meals'::regclass
      AND conname = 'meals_external_id_key'
  ) THEN
    ALTER TABLE public.meals
      ADD CONSTRAINT meals_external_id_key UNIQUE (external_id);
  END IF;
END
$$;
