// supabase/functions/mensa-occupancy-sync/index.ts
//
// Snapshots /api/frequenz into public.canteen_occupancy. Runs every 30
// minutes via cron, but skips early when no canteen is currently open
// (the upstream returns zeros that would pollute the time-series).
//
// 4 entries per call (Zentralmensa, Mensa am Turm, CGiN, Bistro HAWK).
// 90-day retention enforced inline at the tail of the run.

import {
  fetchWithRetry,
  log,
  supabase,
} from '../_shared/supabase.ts';

const URL = 'https://app.studentenwerk-goettingen.de/api/frequenz';

interface FrequenzEntry {
  id: number;
  name: string;
  VerkaeufeAktuell: string;
  VerkaeufeDurchschnittWochentagUhrzeit: string;
  VerkaeufeDurchschnittJahrTotal: string;
  color: string;
  text: string;
}

function num(s: string | undefined): number | null {
  if (!s) return null;
  const n = parseFloat(s);
  return Number.isFinite(n) ? n : null;
}

// Are any of the four mensas currently open? Reads canteen_hours.
// Defaults to "yes" if the table is empty (e.g. mensa-hours-sync hasn't
// been run yet) so we don't silently skip the first invocation.
async function anyMensaOpen(): Promise<boolean> {
  const now = new Date();
  // Compute the German wall-clock weekday + time. Toolbox Date math is in
  // UTC, but Europe/Berlin is UTC+1/+2 — the simple offset works because
  // the upstream is the same TZ.
  const berlin = new Date(now.toLocaleString('en-US', { timeZone: 'Europe/Berlin' }));
  const isoDow = ((berlin.getDay() + 6) % 7) + 1; // JS Sun=0 → ISO 7
  const hhmm = `${berlin.getHours().toString().padStart(2, '0')}:${berlin
    .getMinutes()
    .toString()
    .padStart(2, '0')}:00`;

  const { data, error } = await supabase
    .from('canteen_hours')
    .select('open_time, close_time, canteens!inner(slug)')
    .eq('day_of_week', isoDow)
    .not('open_time', 'is', null);
  if (error) {
    log('hours probe error', error);
    return true; // fail-open
  }
  if (!data || data.length === 0) return true; // first run, hours not synced yet
  // Restrict to the four mensa slugs — cafés don't appear in /api/frequenz.
  const mensaSlugs = new Set(['zentral', 'turm', 'cgin', 'hawk']);
  return data.some((r: any) => {
    const slug = r.canteens?.slug;
    if (slug && !mensaSlugs.has(slug)) return false;
    return r.open_time && r.close_time && hhmm >= r.open_time && hhmm <= r.close_time;
  });
}

Deno.serve(async () => {
  log('── mensa-occupancy-sync ──');
  if (!(await anyMensaOpen())) {
    log('no mensa open → skip');
    return new Response(JSON.stringify({ ok: true, skipped: 'closed' }), {
      headers: { 'Content-Type': 'application/json' },
    });
  }

  const res = await fetchWithRetry(URL, 'application/json');
  if (!res || !res.ok) return new Response('upstream fetch failed', { status: 502 });
  let entries: FrequenzEntry[];
  try {
    entries = (await res.json()) as FrequenzEntry[];
  } catch (e) {
    log('parse error', e);
    return new Response('upstream parse failed', { status: 502 });
  }

  // Resolve canteen ids by external_id, batched.
  const ids = entries.map((e) => e.id).filter((n) => Number.isFinite(n));
  const { data: canteenRows, error: lookupErr } = await supabase
    .from('canteens')
    .select('id, external_id')
    .in('external_id', ids);
  if (lookupErr) {
    log('canteen lookup error', lookupErr);
    return new Response('lookup failed', { status: 500 });
  }
  const idByExternal = new Map<number, string>();
  for (const row of canteenRows ?? []) {
    if (row.external_id != null) idByExternal.set(row.external_id, row.id);
  }

  const observedAt = new Date().toISOString();
  const rows = entries
    .filter((e) => idByExternal.has(e.id))
    .map((e) => ({
      canteen_id: idByExternal.get(e.id)!,
      observed_at: observedAt,
      sales_current: num(e.VerkaeufeAktuell),
      sales_avg_weekday: num(e.VerkaeufeDurchschnittWochentagUhrzeit),
      sales_avg_yearly: num(e.VerkaeufeDurchschnittJahrTotal),
      color: e.color || null,
      status_key: e.text || null,
    }));

  if (rows.length > 0) {
    const { error } = await supabase.from('canteen_occupancy').insert(rows);
    if (error) {
      log('insert error', error);
      return new Response(`insert failed: ${error.message}`, { status: 500 });
    }
  }

  // Retention: drop snapshots older than 90 days.
  const cutoff = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString();
  const { error: pruneErr } = await supabase
    .from('canteen_occupancy')
    .delete()
    .lt('observed_at', cutoff);
  if (pruneErr) log('prune error', pruneErr);

  log(`inserted ${rows.length} snapshots`);
  return new Response(
    JSON.stringify({ ok: true, inserted: rows.length, observed_at: observedAt }),
    { headers: { 'Content-Type': 'application/json' } },
  );
});
