// Supabase service-role client shared across mensa-* sync functions.
// Service role is required for upserts since RLS blocks anon writes.

// deno-lint-ignore-file no-explicit-any
import { createClient, SupabaseClient } from 'https://esm.sh/@supabase/supabase-js@2.39.6';

const SUPABASE_URL = Deno.env.get('SUPABASE_URL');
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
  throw new Error('Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY');
}

export const supabase: SupabaseClient = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { persistSession: false },
});

export const log = (msg: string, ...args: any[]) =>
  console.log(`${new Date().toISOString()}  ${msg}`, ...args);

export const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

// ───── HTTP fetch with retry/backoff ─────
const USER_AGENT = 'MensaApp-Scraper/2.0 (+https://github.com/larskaesberg)';
const FETCH_TIMEOUT_MS = 15_000;
const FETCH_ATTEMPTS = 3;

export async function fetchWithRetry(url: string, accept = 'application/json'): Promise<Response | null> {
  let lastErr: unknown;
  for (let i = 0; i < FETCH_ATTEMPTS; i++) {
    try {
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), FETCH_TIMEOUT_MS);
      const res = await fetch(url, {
        headers: { 'User-Agent': USER_AGENT, Accept: accept },
        signal: ctrl.signal,
      });
      clearTimeout(timer);
      if (res.status === 404) return res;
      if (res.ok || (res.status >= 400 && res.status < 500 && res.status !== 429)) {
        return res;
      }
      lastErr = new Error(`HTTP ${res.status}`);
    } catch (e) {
      lastErr = e;
    }
    if (i < FETCH_ATTEMPTS - 1) {
      const backoff = 500 * 2 ** i;
      log(`fetch retry ${i + 1}/${FETCH_ATTEMPTS - 1} in ${backoff}ms (${url}): ${lastErr}`);
      await sleep(backoff);
    }
  }
  log(`fetch giving up on ${url}: ${lastErr}`);
  return null;
}

export async function mapWithConcurrency<T, U>(
  items: T[],
  n: number,
  fn: (t: T) => Promise<U>,
): Promise<U[]> {
  const out: U[] = new Array(items.length);
  let cursor = 0;
  await Promise.all(
    Array.from({ length: Math.min(n, items.length) }, async () => {
      while (true) {
        const idx = cursor++;
        if (idx >= items.length) return;
        out[idx] = await fn(items[idx]);
      }
    }),
  );
  return out;
}

// Resolve canteen_id by name. The migration pre-creates the four mensas
// and backfills their slugs/external_ids. For new entries (e.g. cafés
// from /api/oeffnungszeiten) we upsert by name.
const canteenIdCache = new Map<string, string>();

export async function getOrCreateCanteenId(name: string): Promise<string> {
  const cached = canteenIdCache.get(name);
  if (cached) return cached;
  const { data, error } = await supabase
    .from('canteens')
    .upsert({ name }, { onConflict: 'name' })
    .select('id')
    .single();
  if (error) throw error;
  canteenIdCache.set(name, data!.id);
  return data!.id;
}
