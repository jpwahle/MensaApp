-- Auto-inherit `image_path_generic` from existing meals with the same title
-- on row creation. The smooth-endpoint function already propagates *forward*
-- (when it generates a new generic image, it updates every row with that
-- title); this trigger covers the *backward* case — when a fresh row is
-- inserted by the scraper for a title that already has a generic image
-- generated against an older row. Without this, newly-scraped rows show up
-- imageless until the next smooth-endpoint run touches them.

CREATE OR REPLACE FUNCTION public.inherit_generic_image() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.image_path_generic IS NULL AND NEW.title IS NOT NULL THEN
    SELECT image_path_generic
      INTO NEW.image_path_generic
    FROM public.meals
    WHERE title = NEW.title
      AND image_path_generic IS NOT NULL
    LIMIT 1;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS meals_inherit_generic_image ON public.meals;
CREATE TRIGGER meals_inherit_generic_image
BEFORE INSERT ON public.meals
FOR EACH ROW
EXECUTE FUNCTION public.inherit_generic_image();

-- Belt-and-suspenders: if image_path_generic is set on a row (or modified),
-- propagate to all siblings with the same title. smooth-endpoint already
-- does this in app code, but this trigger guarantees consistency for any
-- future code path that might forget to update sibling rows.
CREATE OR REPLACE FUNCTION public.propagate_generic_image() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  UPDATE public.meals
  SET image_path_generic = NEW.image_path_generic
  WHERE title = NEW.title
    AND id <> NEW.id
    AND image_path_generic IS DISTINCT FROM NEW.image_path_generic;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS meals_propagate_generic_image ON public.meals;
CREATE TRIGGER meals_propagate_generic_image
AFTER UPDATE OF image_path_generic ON public.meals
FOR EACH ROW
WHEN (NEW.image_path_generic IS NOT NULL
      AND NEW.image_path_generic IS DISTINCT FROM OLD.image_path_generic)
EXECUTE FUNCTION public.propagate_generic_image();

-- One-time backfill so you can stop running the hourly cron immediately.
-- Same query you've been running, but only once.
UPDATE public.meals AS m
SET image_path_generic = (
  SELECT m2.image_path_generic
  FROM public.meals AS m2
  WHERE m2.title = m.title AND m2.image_path_generic IS NOT NULL
  LIMIT 1
)
WHERE m.image_path_generic IS NULL;
