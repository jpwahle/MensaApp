-- Persist the upstream <mittag> integer per meal_date so we can drive the
-- Mittag / Nachmittag split off a structured signal rather than the free-
-- text `note` heuristic. Semantics observed live (2026-05-06):
--   0 → served whenever the canteen is open (default, all non-Zentralmensa
--       dishes plus the afternoon-capable items at Zentralmensa).
--   >0 → lunch-only (Zentralmensa "Aktion", "CampusCurry", "Teppan Yaki",
--       "Salatbuffet", etc. — explicitly not part of the Nachmittag menu).
ALTER TABLE public.meal_dates
  ADD COLUMN IF NOT EXISTS mittag smallint;
