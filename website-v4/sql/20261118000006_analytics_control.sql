-- BADSHAH AI — Migration 006 (ANALYTICS + USERS + SITE/APP CONTROL) — idempotent, additive only.
-- Run once in Supabase SQL Editor (project aghlacmqlagmigoznxlm). Safe to re-run.
-- RLS is ON for every table and there are NO public policies except site_settings (read-only,
-- non-secret config). All writes happen through the Cloudflare Worker (service role).

-- 1) visitor sessions (one row per browser session; heartbeat keeps last_seen fresh)
create table if not exists public.site_sessions (
  sid text primary key,
  vid text,
  uid text,
  email text,
  name text,
  first_seen timestamptz not null default now(),
  last_seen timestamptz not null default now(),
  pages integer not null default 1,
  landing text,
  path_last text,
  referrer text,
  country text,
  city text,
  device text,
  browser text,
  os text,
  is_new boolean not null default true
);
create index if not exists site_sessions_last_seen_idx on public.site_sessions (last_seen desc);
create index if not exists site_sessions_first_seen_idx on public.site_sessions (first_seen desc);
alter table public.site_sessions enable row level security;

-- 2) page views / events
create table if not exists public.site_events (
  id bigserial primary key,
  at timestamptz not null default now(),
  sid text,
  vid text,
  uid text,
  type text not null default 'pageview',
  path text,
  title text,
  meta jsonb
);
create index if not exists site_events_at_idx on public.site_events (at desc);
create index if not exists site_events_type_idx on public.site_events (type, at desc);
alter table public.site_events enable row level security;

-- 3) web users (everyone who signs up / logs in on the website or app)
create table if not exists public.web_users (
  firebase_uid text primary key,
  email text,
  name text,
  provider text,
  first_seen timestamptz not null default now(),
  last_seen timestamptz not null default now(),
  logins integer not null default 1,
  blocked boolean not null default false,
  note text
);
create index if not exists web_users_last_seen_idx on public.web_users (last_seen desc);
alter table public.web_users enable row level security;

-- 4) site / app config (public read, written only by the Worker)
create table if not exists public.site_settings (
  key text primary key,
  value jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
alter table public.site_settings enable row level security;
drop policy if exists "site_settings public read" on public.site_settings;
create policy "site_settings public read" on public.site_settings for select using (true);

-- 5) device commands (web -> phone / computer control queue; the Android app / desktop agent consumes it)
create table if not exists public.device_commands (
  id uuid primary key default gen_random_uuid(),
  firebase_uid text,
  target text not null default 'phone',
  command text not null,
  status text not null default 'pending',
  result text,
  created_at timestamptz not null default now(),
  done_at timestamptz
);
create index if not exists device_commands_uid_idx on public.device_commands (firebase_uid, created_at desc);
alter table public.device_commands enable row level security;

-- 6) announcements table (used by the admin "Announce" tab; created only if the app backend lacks it)
create table if not exists public.announcements (
  id uuid primary key default gen_random_uuid(),
  title text,
  body text,
  active boolean not null default true,
  created_at timestamptz not null default now()
);
alter table public.announcements enable row level security;
drop policy if exists "announcements public read" on public.announcements;
create policy "announcements public read" on public.announcements for select using (active = true);

-- 7) one-call analytics for the admin panel
create or replace function public.bh_analytics(p_days integer default 14)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  d integer := greatest(1, least(coalesce(p_days, 14), 365));
  since timestamptz := date_trunc('day', now()) - ((d - 1) || ' days')::interval;
  res jsonb;
begin
  select jsonb_build_object(
    'totals', (select jsonb_build_object(
        'visitors',   count(distinct coalesce(vid, sid)),
        'sessions',   count(*),
        'newVisitors',count(distinct coalesce(vid, sid)) filter (where is_new),
        'avgSessionSec', coalesce(round(avg(extract(epoch from (last_seen - first_seen)))::numeric, 0), 0),
        'bounceRate', coalesce(round((100.0 * count(*) filter (where pages <= 1) / nullif(count(*), 0))::numeric, 1), 0)
      ) from site_sessions where first_seen >= since),
    'pageviews', (select count(*) from site_events where type = 'pageview' and at >= since),
    'signups',   (select count(*) from web_users where first_seen >= since),
    'activeNow', (select count(*) from site_sessions where last_seen >= now() - interval '2 minutes'),
    'activeUsers24h', (select count(*) from web_users where last_seen >= now() - interval '24 hours'),
    'totalUsers', (select count(*) from web_users),
    'blockedUsers', (select count(*) from web_users where blocked),
    'daily', (select coalesce(jsonb_agg(row_to_json(x) order by x.d), '[]'::jsonb) from (
        select to_char(g.day, 'YYYY-MM-DD') as d,
               coalesce((select count(distinct coalesce(vid, sid)) from site_sessions s where s.first_seen >= g.day and s.first_seen < g.day + interval '1 day'), 0) as visitors,
               coalesce((select count(*) from site_sessions s where s.first_seen >= g.day and s.first_seen < g.day + interval '1 day'), 0) as sessions,
               coalesce((select count(*) from site_events e where e.type = 'pageview' and e.at >= g.day and e.at < g.day + interval '1 day'), 0) as pageviews,
               coalesce((select count(*) from web_users u where u.first_seen >= g.day and u.first_seen < g.day + interval '1 day'), 0) as signups
        from generate_series(since, date_trunc('day', now()), interval '1 day') as g(day)) x),
    'pages', (select coalesce(jsonb_agg(row_to_json(x)), '[]'::jsonb) from (
        select path, count(*) as views, count(distinct coalesce(vid, sid)) as visitors
        from site_events where type = 'pageview' and at >= since group by path order by count(*) desc limit 12) x),
    'countries', (select coalesce(jsonb_agg(row_to_json(x)), '[]'::jsonb) from (
        select coalesce(nullif(country, ''), '??') as k, count(*) as v from site_sessions where first_seen >= since group by 1 order by 2 desc limit 12) x),
    'devices', (select coalesce(jsonb_agg(row_to_json(x)), '[]'::jsonb) from (
        select coalesce(nullif(device, ''), 'other') as k, count(*) as v from site_sessions where first_seen >= since group by 1 order by 2 desc) x),
    'browsers', (select coalesce(jsonb_agg(row_to_json(x)), '[]'::jsonb) from (
        select coalesce(nullif(browser, ''), 'other') as k, count(*) as v from site_sessions where first_seen >= since group by 1 order by 2 desc limit 8) x),
    'referrers', (select coalesce(jsonb_agg(row_to_json(x)), '[]'::jsonb) from (
        select coalesce(nullif(referrer, ''), 'direct') as k, count(*) as v from site_sessions where first_seen >= since group by 1 order by 2 desc limit 10) x),
    'hours', (select coalesce(jsonb_agg(c order by h), '[]'::jsonb) from (
        select h, (select count(*) from site_sessions s where s.first_seen >= since and extract(hour from s.first_seen at time zone 'Asia/Karachi') = h) as c
        from generate_series(0, 23) as h) t)
  ) into res;
  return res;
end;
$$;
revoke all on function public.bh_analytics(integer) from public, anon, authenticated;
grant execute on function public.bh_analytics(integer) to service_role;

select 'BADSHAH AI MIGRATION 006 (ANALYTICS + CONTROL) COMPLETED' as result, now() as at;
