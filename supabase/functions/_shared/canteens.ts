// Source-of-truth maps for the four canteens the menu API exposes.
// Used by mensa-occupancy-sync to look up canteens by external id and by
// mensa-menu-sync to assign slugs to newly-created canteens.

export const CANTEEN_SLUGS: Record<string, string> = {
  'Zentralmensa': 'zentral',
  'Mensa am Turm': 'turm',
  'CGiN': 'cgin',
  'Bistro HAWK': 'hawk',
};

// /api/frequenz response keys these by integer id; we mirror the same.
export const CANTEEN_EXTERNAL_IDS: Record<string, number> = {
  'Zentralmensa': 4014,
  'Mensa am Turm': 4155,
  'CGiN': 4209,
  'Bistro HAWK': 4272,
};
