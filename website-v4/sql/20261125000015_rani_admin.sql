-- ============================================================
-- BADSHAH-RANI AI — Migration 015: RANI admin panel
-- RANI AI GF's separate admin panel (badshah222-ai.pages.dev/Ranipanal)
-- and the RANI section on the BADSHAH website.
--
-- RUN: Supabase Dashboard → SQL Editor → paste → Run (once only).
-- PREPARE ONLY — do NOT run this file yourself; the admin (coordinator) will run it.
-- Idempotent: running it again is safe.
--
-- What it does:
--  1. `app` marker ('badshah' / 'rani') on shared tables — RANI rows
--     show up separately from BADSHAH rows. Default is 'badshah', so
--     nothing changes for the BADSHAH app/website.
--  2. RANI-only tables: rani_activity (records), rani_preferences,
--     rani_provider_settings, rani_screen_recordings (metadata only),
--     rani_voiceprints (metadata only), app_users, app_settings.
--  3. RPCs app_touch_profile / app_record_login / app_record_usage get an
--     optional p_app parameter (default 'badshah') — old
--     calls keep working exactly as before.
--  4. RANI's 6 female voices are seeded in voice_profiles
--     (Aoede default) plus RANI's default controls in app_settings.
--
-- RANI APP SIDE (for the native app team — with the migration or
-- after it, otherwise RANI data counts as 'badshah'):
--  * Send app='rani' in every Supabase call:
--      - conversations / messages / support_tickets / support_messages /
--        memories POST body mein "app": "rani"
--      - RPCs: app_touch_profile(..., p_app => 'rani'),
--        app_record_login(..., p_app => 'rani'),
--        app_record_usage(..., p_app => 'rani')
--  * Do NOT write user_preferences / user_provider_settings (shared row,
--    BADSHAH app is closed — its PK cannot change). Use
--    rani_preferences / rani_provider_settings tables instead
--    (created below, same shape).
--  * Log every RANI action in rani_activity (event_type
--    check list is below). Counts/metadata only — never message
--    content, contacts data, or recording files.
--  * Screen recordings: metadata only in rani_screen_recordings
--    (file_name, duration_sec, size_bytes) — the file stays on the phone.
--  * Voice-print enrolment: metadata only in rani_voiceprints —
--    never send raw voice data.
--  * Read feature switches + maintenance from app_settings (app='rani').
--    Maintenance key 'maintenance', features key 'features',
--    persona key 'persona', gemini key 'gemini', announcement 'announcement'.
--
-- PRIVACY (standing rule):
--  * Contacts, message content, recordings, raw voice — never
--    send to Supabase; everything stays on-device. The panel shows only
--    counts and metadata.
-- ============================================================

-- ---------- 1. `app` marker on shared tables ----------
alter table public.conversations add column if not exists app text not null default 'badshah';
alter table public.messages add column if not exists app text not null default 'badshah';
alter table public.ai_usage_events add column if not exists app text not null default 'badshah';
alter table public.login_events add column if not exists app text not null default 'badshah';
alter table public.support_tickets add column if not exists app text not null default 'badshah';
alter table public.support_messages add column if not exists app text not null default 'badshah';
alter table public.memories add column if not exists app text not null default 'badshah';
alter table public.app_events add column if not exists app text not null default 'badshah';

create index if not exists idx_conversations_app_created on public.conversations (app, created_at desc);
create index if not exists idx_messages_app_created on public.messages (app, created_at desc);
create index if not exists idx_usage_app_created on public.ai_usage_events (app, created_at desc);
create index if not exists idx_login_app_created on public.login_events (app, created_at desc);
create index if not exists idx_tickets_app_status on public.support_tickets (app, status, created_at desc);
create index if not exists idx_memories_app_user on public.memories (app, user_id, created_at desc);

-- ---------- 2. app_users: which user is in which app ----------
-- profiles is shared (one user can use BADSHAH + RANI both),
-- so per-app membership lives in a separate table.
create table if not exists public.app_users (
    user_id text not null,
    app text not null default 'badshah' check (app in ('badshah', 'rani')),
    first_seen_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    primary key (user_id, app)
);
alter table public.app_users enable row level security;
create index if not exists idx_app_users_app_seen on public.app_users (app, last_seen_at desc);
revoke all on public.app_users from anon, authenticated;
grant all on public.app_users to service_role;

-- ---------- 3. rani_preferences (RANI version of user_preferences) ----------
-- BADSHAH app is CLOSED — user_preferences PK (user_id) cannot change,
-- so RANI preferences live in a separate table.
create table if not exists public.rani_preferences (
    user_id text primary key,
    selected_provider text default 'gemini',
    selected_model text,
    selected_voice text default 'Aoede',
    voice_engine text,
    speech_rate numeric default 1.0,
    speech_pitch numeric default 1.0,
    language text default 'en',
    personality text default 'girlfriend',
    memory_enabled boolean default true,
    notifications_enabled boolean default true,
    updated_at timestamptz not null default now()
);
alter table public.rani_preferences enable row level security;
drop policy if exists "rani_preferences_select_own" on public.rani_preferences;
create policy "rani_preferences_select_own" on public.rani_preferences for select to authenticated
    using (user_id = (select auth.jwt()->>'sub'));
drop policy if exists "rani_preferences_insert_own" on public.rani_preferences;
create policy "rani_preferences_insert_own" on public.rani_preferences for insert to authenticated
    with check (user_id = (select auth.jwt()->>'sub'));
drop policy if exists "rani_preferences_update_own" on public.rani_preferences;
create policy "rani_preferences_update_own" on public.rani_preferences for update to authenticated
    using (user_id = (select auth.jwt()->>'sub'))
    with check (user_id = (select auth.jwt()->>'sub'));
revoke all on public.rani_preferences from anon, authenticated;
grant select, insert, update on public.rani_preferences to authenticated;
grant all on public.rani_preferences to service_role;

-- ---------- 4. rani_provider_settings ----------
create table if not exists public.rani_provider_settings (
    user_id text not null,
    provider text not null,
    is_configured boolean not null default false,
    key_fingerprint text,
    connection_status text not null default 'not_configured',
    last_tested_at timestamptz,
    updated_at timestamptz not null default now(),
    primary key (user_id, provider)
);
alter table public.rani_provider_settings enable row level security;
drop policy if exists "rani_provider_select_own" on public.rani_provider_settings;
create policy "rani_provider_select_own" on public.rani_provider_settings for select to authenticated
    using (user_id = (select auth.jwt()->>'sub'));
drop policy if exists "rani_provider_insert_own" on public.rani_provider_settings;
create policy "rani_provider_insert_own" on public.rani_provider_settings for insert to authenticated
    with check (user_id = (select auth.jwt()->>'sub'));
drop policy if exists "rani_provider_update_own" on public.rani_provider_settings;
create policy "rani_provider_update_own" on public.rani_provider_settings for update to authenticated
    using (user_id = (select auth.jwt()->>'sub'))
    with check (user_id = (select auth.jwt()->>'sub'));
drop policy if exists "rani_provider_delete_own" on public.rani_provider_settings;
create policy "rani_provider_delete_own" on public.rani_provider_settings for delete to authenticated
    using (user_id = (select auth.jwt()->>'sub'));
revoke all on public.rani_provider_settings from anon, authenticated;
grant select, insert, update, delete on public.rani_provider_settings to authenticated;
grant all on public.rani_provider_settings to service_role;

-- ---------- 5. rani_activity: records (counts/metadata ONLY) ----------
create table if not exists public.rani_activity (
    id uuid primary key default gen_random_uuid(),
    user_id text not null,
    event_type text not null check (event_type in (
        'chat', 'voice_call', 'voice_minutes', 'command',
        'youtube_search', 'message_sent', 'contacts_read',
        'screen_recording', 'camera_event', 'face_recognition',
        'voiceprint_enrol', 'face_lock', 'safety_alert', 'awareness'
    )),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
create index if not exists idx_rani_activity_user_created on public.rani_activity (user_id, created_at desc);
create index if not exists idx_rani_activity_type_created on public.rani_activity (event_type, created_at desc);
alter table public.rani_activity enable row level security;
drop policy if exists "rani_activity_select_own" on public.rani_activity;
create policy "rani_activity_select_own" on public.rani_activity for select to authenticated
    using (user_id = (select auth.jwt()->>'sub'));
drop policy if exists "rani_activity_insert_own" on public.rani_activity;
create policy "rani_activity_insert_own" on public.rani_activity for insert to authenticated
    with check (user_id = (select auth.jwt()->>'sub'));
revoke all on public.rani_activity from anon, authenticated;
grant select, insert on public.rani_activity to authenticated;
grant all on public.rani_activity to service_role;

-- ---------- 6. rani_screen_recordings: metadata ONLY (file stays on phone) ----------
create table if not exists public.rani_screen_recordings (
    id uuid primary key default gen_random_uuid(),
    user_id text not null,
    file_name text not null,
    duration_sec integer not null default 0,
    size_bytes bigint not null default 0,
    created_at timestamptz not null default now()
);
create index if not exists idx_rani_recordings_user on public.rani_screen_recordings (user_id, created_at desc);
alter table public.rani_screen_recordings enable row level security;
drop policy if exists "rani_recordings_select_own" on public.rani_screen_recordings;
create policy "rani_recordings_select_own" on public.rani_screen_recordings for select to authenticated
    using (user_id = (select auth.jwt()->>'sub'));
drop policy if exists "rani_recordings_insert_own" on public.rani_screen_recordings;
create policy "rani_recordings_insert_own" on public.rani_screen_recordings for insert to authenticated
    with check (user_id = (select auth.jwt()->>'sub'));
revoke all on public.rani_screen_recordings from anon, authenticated;
grant select, insert on public.rani_screen_recordings to authenticated;
grant all on public.rani_screen_recordings to service_role;

-- ---------- 7. rani_voiceprints: enrolment metadata ONLY (never raw voice) ----------
create table if not exists public.rani_voiceprints (
    id uuid primary key default gen_random_uuid(),
    user_id text not null,
    enrolled_at timestamptz not null default now(),
    model_version text,
    is_active boolean not null default true,
    last_verified_at timestamptz
);
create index if not exists idx_rani_voiceprints_user on public.rani_voiceprints (user_id, enrolled_at desc);
alter table public.rani_voiceprints enable row level security;
drop policy if exists "rani_voiceprints_select_own" on public.rani_voiceprints;
create policy "rani_voiceprints_select_own" on public.rani_voiceprints for select to authenticated
    using (user_id = (select auth.jwt()->>'sub'));
drop policy if exists "rani_voiceprints_insert_own" on public.rani_voiceprints;
create policy "rani_voiceprints_insert_own" on public.rani_voiceprints for insert to authenticated
    with check (user_id = (select auth.jwt()->>'sub'));
drop policy if exists "rani_voiceprints_update_own" on public.rani_voiceprints;
create policy "rani_voiceprints_update_own" on public.rani_voiceprints for update to authenticated
    using (user_id = (select auth.jwt()->>'sub'))
    with check (user_id = (select auth.jwt()->>'sub'));
revoke all on public.rani_voiceprints from anon, authenticated;
grant select, insert, update on public.rani_voiceprints to authenticated;
grant all on public.rani_voiceprints to service_role;

-- ---------- 8. app_settings: all RANI controls (admin writes, app reads) ----------
-- Never store secrets here — non-secret config only.
create table if not exists public.app_settings (
    app text not null check (app in ('badshah', 'rani')),
    setting_key text not null,
    setting_value jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now(),
    primary key (app, setting_key)
);
alter table public.app_settings enable row level security;
drop policy if exists "app_settings_read_authenticated" on public.app_settings;
create policy "app_settings_read_authenticated" on public.app_settings for select to authenticated
    using (true);
revoke all on public.app_settings from anon, authenticated;
grant select on public.app_settings to authenticated;
grant all on public.app_settings to service_role;

-- RANI defaults (can be changed from the admin panel)
insert into public.app_settings (app, setting_key, setting_value) values
  ('rani', 'features', '{"awareness": true, "camera": true, "screen_recording": true, "voiceprint_gate": true, "floating_orb": true}'),
  ('rani', 'persona', '{"default_voice": "Aoede", "tone": "loving", "call_greeting": "G Babu"}'),
  ('rani', 'gemini', '{"model": "gemini-3.8-flash"}'),
  ('rani', 'maintenance', '{"enabled": false, "message": ""}'),
  ('rani', 'announcement', '{"enabled": false, "title": "", "body": ""}')
on conflict (app, setting_key) do nothing;

-- ---------- 9. RANI voices seed (female ONLY, Aoede default) ----------
insert into public.voice_profiles (id, label, category, language, engine, pitch, speed, is_enabled, sort_order, notes) values
  ('Aoede', 'Aoede · default', 'female', 'en', 'gemini_tts', 1.0, 1.0, true, 1, 'RANI default voice'),
  ('Kore', 'Kore', 'female', 'en', 'gemini_tts', 1.0, 1.0, true, 2, 'RANI female voice'),
  ('Zephyr', 'Zephyr', 'female', 'en', 'gemini_tts', 1.0, 1.0, true, 3, 'RANI female voice'),
  ('Leda', 'Leda', 'female', 'en', 'gemini_tts', 1.0, 1.0, true, 4, 'RANI female voice'),
  ('Sulafat', 'Sulafat', 'female', 'en', 'gemini_tts', 1.0, 1.0, true, 5, 'RANI female voice'),
  ('Callirrhoe', 'Callirrhoe', 'female', 'en', 'gemini_tts', 1.0, 1.0, true, 6, 'RANI female voice')
on conflict (id) do nothing;

-- ---------- 10. RPCs: optional p_app (backward compatible) ----------
-- app_touch_profile: registers RANI users in app_users.
drop function if exists public.app_touch_profile(text, text, text);
create or replace function public.app_touch_profile(
    p_email text, p_display_name text, p_avatar_url text, p_app text default 'badshah')
returns jsonb language plpgsql security definer set search_path = '' as $$
declare uid text := (select auth.jwt()->>'sub'); blocked boolean; use_app text;
begin
    if uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;
    use_app := case when p_app = 'rani' then 'rani' else 'badshah' end;
    insert into public.profiles (id, email, display_name, avatar_url, last_seen_at)
    values (uid, left(p_email,320), left(p_display_name,120), left(p_avatar_url,1000), now())
    on conflict (id) do update set
        email = coalesce(excluded.email, public.profiles.email),
        display_name = coalesce(public.profiles.display_name, excluded.display_name),
        avatar_url = coalesce(public.profiles.avatar_url, excluded.avatar_url),
        last_seen_at = now(), updated_at = now();
    insert into public.app_users (user_id, app, first_seen_at, last_seen_at)
    values (uid, use_app, now(), now())
    on conflict (user_id, app) do update set last_seen_at = now();
    select is_blocked into blocked from public.profiles where id = uid;
    return jsonb_build_object('user_id', uid, 'is_blocked', blocked);
end; $$;

-- app_record_login: app marker in login_events.
drop function if exists public.app_record_login(text, text, text);
create or replace function public.app_record_login(
    p_provider text, p_event_type text default 'login', p_device text default 'android', p_app text default 'badshah')
returns void language plpgsql security definer set search_path = '' as $$
declare uid text := (select auth.jwt()->>'sub');
begin
    if uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;
    if p_event_type not in ('login','register','logout') then
        raise exception 'bad event_type'; end if;
    if not exists (select 1 from public.profiles where id = uid) then
        raise exception 'profile missing; call app_touch_profile first'; end if;
    insert into public.login_events (user_id, provider, event_type, success, device_type, app)
    values (uid, left(p_provider,40), p_event_type, true, left(p_device,40),
            case when p_app = 'rani' then 'rani' else 'badshah' end);
end; $$;

-- app_record_usage: app marker in ai_usage_events.
drop function if exists public.app_record_usage(text, text, text, boolean, bigint, bigint, integer, text);
create or replace function public.app_record_usage(
    p_provider text, p_model text, p_event_type text, p_success boolean,
    p_input_tokens bigint default 0, p_output_tokens bigint default 0,
    p_latency_ms integer default null, p_error_code text default null, p_app text default 'badshah')
returns void language plpgsql security definer set search_path = '' as $$
declare uid text := (select auth.jwt()->>'sub');
begin
    if uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;
    if p_provider not in ('gemini','groq','elevenlabs','android_tts') then
        raise exception 'unknown provider'; end if;
    if public.is_blocked_user() then raise exception 'account blocked'; end if;
    insert into public.ai_usage_events
      (user_id, provider, model_name, event_type, success, input_tokens, output_tokens, latency_ms, error_code, app)
    values (uid, p_provider, left(p_model,120), left(coalesce(p_event_type,'chat'),40), coalesce(p_success,false),
            greatest(coalesce(p_input_tokens,0),0), greatest(coalesce(p_output_tokens,0),0),
            p_latency_ms, left(p_error_code,80),
            case when p_app = 'rani' then 'rani' else 'badshah' end);
end; $$;

revoke all on function public.app_touch_profile(text,text,text,text),
                       public.app_record_login(text,text,text,text),
                       public.app_record_usage(text,text,text,boolean,bigint,bigint,integer,text,text)
    from public, anon;
grant execute on function public.app_touch_profile(text,text,text,text),
                          public.app_record_login(text,text,text,text),
                          public.app_record_usage(text,text,text,boolean,bigint,bigint,integer,text,text)
    to authenticated;

-- Blocked users: also applies to the new RANI tables.
do $$
declare t text;
begin
  foreach t in array array['rani_activity','rani_screen_recordings','rani_voiceprints',
                           'rani_preferences','rani_provider_settings'] loop
    execute format('drop policy if exists %I on public.%I', 'blocked_users_no_insert', t);
    execute format('create policy %I on public.%I as restrictive for insert to authenticated with check (not public.is_blocked_user())', 'blocked_users_no_insert', t);
  end loop;
end $$;

select 'BADSHAH-RANI AI MIGRATION 015 (RANI ADMIN) COMPLETED' as status, now() as completed_at;
