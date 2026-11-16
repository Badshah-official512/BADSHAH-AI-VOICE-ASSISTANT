-- =====================================================
-- BADSHAH AI - COMPLETE DATABASE MIGRATION
-- Firebase Authentication + Supabase + Admin Panel
-- =====================================================

-- 01. EXTENSIONS
create extension if not exists pgcrypto with schema extensions;

-- 02. PRIVATE SCHEMA
create schema if not exists private;

-- =====================================================
-- 03. USER PROFILES
-- =====================================================

create table if not exists public.profiles (
    id text primary key,
    email text,
    display_name text,
    avatar_url text,
    is_blocked boolean not null default false,
    created_at timestamptz not null default now(),
    last_seen_at timestamptz,
    updated_at timestamptz not null default now()
);

-- =====================================================
-- 04. LOGIN HISTORY
-- =====================================================

create table if not exists public.login_events (
    id uuid primary key default gen_random_uuid(),
    user_id text not null references public.profiles(id) on delete cascade,
    provider text,
    event_type text not null default 'login',
    success boolean not null default true,
    device_type text,
    created_at timestamptz not null default now()
);

-- =====================================================
-- 05. CONVERSATIONS
-- =====================================================

create table if not exists public.conversations (
    id uuid primary key default gen_random_uuid(),
    user_id text not null references public.profiles(id) on delete cascade,
    title text not null default 'New Chat',
    ai_provider text,
    model_name text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- =====================================================
-- 06. CHAT MESSAGES
-- =====================================================

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.conversations(id) on delete cascade,
    user_id text not null references public.profiles(id) on delete cascade,
    role text not null check (role in ('user','assistant','system')),
    content text not null,
    ai_provider text,
    model_name text,
    created_at timestamptz not null default now()
);

-- =====================================================
-- 07. AI USAGE ANALYTICS
-- =====================================================

create table if not exists public.ai_usage_events (
    id uuid primary key default gen_random_uuid(),
    user_id text references public.profiles(id) on delete set null,
    provider text not null,
    model_name text,
    event_type text not null default 'chat',
    success boolean not null default false,
    input_tokens bigint default 0,
    output_tokens bigint default 0,
    latency_ms integer,
    error_code text,
    created_at timestamptz not null default now()
);

-- =====================================================
-- 08. API CONFIGURATION STATUS
-- NO RAW API KEYS IN THIS TABLE
-- =====================================================

create table if not exists public.user_provider_settings (
    id uuid primary key default gen_random_uuid(),
    user_id text not null references public.profiles(id) on delete cascade,
    provider text not null,
    is_configured boolean not null default false,
    key_fingerprint text,
    connection_status text not null default 'not_configured',
    last_tested_at timestamptz,
    updated_at timestamptz not null default now(),
    unique(user_id, provider)
);

-- =====================================================
-- 09. USER PREFERENCES
-- =====================================================

create table if not exists public.user_preferences (
    user_id text primary key references public.profiles(id) on delete cascade,
    selected_provider text default 'gemini',
    selected_model text,
    selected_voice text,
    voice_engine text,
    speech_rate numeric default 1.0,
    speech_pitch numeric default 1.0,
    language text default 'en',
    personality text default 'friendly',
    memory_enabled boolean default true,
    notifications_enabled boolean default true,
    updated_at timestamptz not null default now()
);

-- =====================================================
-- 10. SUPPORT TICKETS
-- =====================================================

create table if not exists public.support_tickets (
    id uuid primary key default gen_random_uuid(),
    user_id text not null references public.profiles(id) on delete cascade,
    subject text not null,
    category text default 'general',
    message text not null,
    status text not null default 'open'
      check(status in ('open','in_progress','resolved','closed')),
    priority text default 'normal',
    admin_reply text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- =====================================================
-- 11. SUPPORT MESSAGES
-- =====================================================

create table if not exists public.support_messages (
    id uuid primary key default gen_random_uuid(),
    ticket_id uuid not null references public.support_tickets(id) on delete cascade,
    sender_type text not null check(sender_type in ('user','admin')),
    message text not null,
    created_at timestamptz not null default now()
);

-- =====================================================
-- 12. APP ACTIVITY
-- =====================================================

create table if not exists public.app_events (
    id uuid primary key default gen_random_uuid(),
    user_id text references public.profiles(id) on delete set null,
    event_name text not null,
    event_details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

-- =====================================================
-- 13. ANNOUNCEMENTS
-- =====================================================

create table if not exists public.announcements (
    id uuid primary key default gen_random_uuid(),
    title text not null,
    content text not null,
    is_published boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- =====================================================
-- 14. PUBLIC APP CONFIGURATION
-- Never store secrets here.
-- =====================================================

create table if not exists public.app_config (
    config_key text primary key,
    config_value jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);

-- =====================================================
-- 15. PRIVATE ADMIN CREDENTIALS
-- =====================================================

create table if not exists private.admin_credentials (
    id integer primary key check(id = 1),
    password_hash text not null,
    updated_at timestamptz not null default now()
);

-- Initial password: replace the placeholder below BEFORE running on a NEW project.
-- Only its bcrypt hash is stored.
-- ON CONFLICT prevents accidentally resetting an
-- already changed administrator password.

insert into private.admin_credentials
(id, password_hash)
values (
    1,
    extensions.crypt(
        '__INITIAL_ADMIN_PASSWORD__',
        extensions.gen_salt('bf', 12)
    )
)
on conflict (id) do nothing;

-- =====================================================
-- 16. ADMIN SESSIONS
-- Store HASHED session tokens only.
-- =====================================================

create table if not exists private.admin_sessions (
    id uuid primary key default gen_random_uuid(),
    token_hash text not null unique,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    last_seen_at timestamptz,
    revoked_at timestamptz
);

-- =====================================================
-- 17. ADMIN LOGIN ATTEMPTS
-- =====================================================

create table if not exists private.admin_login_attempts (
    id uuid primary key default gen_random_uuid(),
    ip_hash text,
    success boolean not null default false,
    attempted_at timestamptz not null default now()
);

-- =====================================================
-- 18. ADMIN AUDIT LOGS
-- =====================================================

create table if not exists private.admin_audit_logs (
    id uuid primary key default gen_random_uuid(),
    action text not null,
    target_user_id text,
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

-- =====================================================
-- 19. ADMIN PASSWORD VERIFICATION FUNCTION
-- Service-side function only.
-- Never return password hashes.
-- =====================================================

create or replace function public.verify_admin_password(
    p_password text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    stored_hash text;
begin
    if p_password is null
       or length(p_password) < 1
       or length(p_password) > 256 then
        return false;
    end if;

    select password_hash
    into stored_hash
    from private.admin_credentials
    where id = 1;

    if stored_hash is null then
        return false;
    end if;

    return extensions.crypt(p_password, stored_hash) = stored_hash;
end;
$$;

revoke all on function public.verify_admin_password(text)
from public, anon, authenticated;

grant execute on function public.verify_admin_password(text)
to service_role;

-- =====================================================
-- 20. ADMIN PASSWORD ROTATION FUNCTION
-- Called only by authenticated server-side admin code.
-- =====================================================

create or replace function public.change_admin_password(
    p_new_password text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_new_password is null
       or length(p_new_password) < 12
       or length(p_new_password) > 256 then
        return false;
    end if;

    update private.admin_credentials
    set password_hash = extensions.crypt(
        p_new_password,
        extensions.gen_salt('bf', 12)
    ),
    updated_at = now()
    where id = 1;

    return found;
end;
$$;

revoke all on function public.change_admin_password(text)
from public, anon, authenticated;

grant execute on function public.change_admin_password(text)
to service_role;

-- =====================================================
-- 21. INDEXES
-- =====================================================

create index if not exists idx_profiles_created
on public.profiles(created_at desc);

create index if not exists idx_profiles_last_seen
on public.profiles(last_seen_at desc);

create index if not exists idx_login_user
on public.login_events(user_id, created_at desc);

create index if not exists idx_conversations_user
on public.conversations(user_id, updated_at desc);

create index if not exists idx_messages_conversation
on public.messages(conversation_id, created_at);

create index if not exists idx_usage_user
on public.ai_usage_events(user_id, created_at desc);

create index if not exists idx_usage_provider
on public.ai_usage_events(provider, created_at desc);

create index if not exists idx_support_user
on public.support_tickets(user_id, created_at desc);

create index if not exists idx_support_status
on public.support_tickets(status, created_at desc);

create index if not exists idx_events_user
on public.app_events(user_id, created_at desc);

create index if not exists idx_admin_sessions_expiry
on private.admin_sessions(expires_at);

create index if not exists idx_admin_attempts_time
on private.admin_login_attempts(attempted_at desc);

create index if not exists idx_admin_audit_time
on private.admin_audit_logs(created_at desc);

-- =====================================================
-- 22. ENABLE RLS
-- =====================================================

alter table public.profiles enable row level security;
alter table public.login_events enable row level security;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;
alter table public.ai_usage_events enable row level security;
alter table public.user_provider_settings enable row level security;
alter table public.user_preferences enable row level security;
alter table public.support_tickets enable row level security;
alter table public.support_messages enable row level security;
alter table public.app_events enable row level security;
alter table public.announcements enable row level security;
alter table public.app_config enable row level security;

-- =====================================================
-- 23. REMOVE OLD POLICIES BEFORE CREATING
-- =====================================================

drop policy if exists "profile_select_own" on public.profiles;
drop policy if exists "profile_insert_own" on public.profiles;
drop policy if exists "profile_update_own" on public.profiles;

drop policy if exists "login_select_own" on public.login_events;

drop policy if exists "conversation_select_own" on public.conversations;
drop policy if exists "conversation_insert_own" on public.conversations;
drop policy if exists "conversation_update_own" on public.conversations;
drop policy if exists "conversation_delete_own" on public.conversations;

drop policy if exists "message_select_own" on public.messages;
drop policy if exists "message_insert_user" on public.messages;
drop policy if exists "message_delete_own" on public.messages;

drop policy if exists "usage_select_own" on public.ai_usage_events;

drop policy if exists "provider_select_own" on public.user_provider_settings;
drop policy if exists "provider_insert_own" on public.user_provider_settings;
drop policy if exists "provider_update_own" on public.user_provider_settings;
drop policy if exists "provider_delete_own" on public.user_provider_settings;

drop policy if exists "preferences_select_own" on public.user_preferences;
drop policy if exists "preferences_insert_own" on public.user_preferences;
drop policy if exists "preferences_update_own" on public.user_preferences;

drop policy if exists "ticket_select_own" on public.support_tickets;
drop policy if exists "ticket_insert_own" on public.support_tickets;

drop policy if exists "support_message_select_own" on public.support_messages;
drop policy if exists "support_message_insert_own" on public.support_messages;

drop policy if exists "events_select_own" on public.app_events;
drop policy if exists "events_insert_own" on public.app_events;

drop policy if exists "announcement_read_published" on public.announcements;
drop policy if exists "config_read_authenticated" on public.app_config;

-- =====================================================
-- 24. USER PROFILE POLICIES
-- Firebase UID is the JWT subject.
-- =====================================================

create policy "profile_select_own"
on public.profiles for select to authenticated
using (id = (select auth.jwt()->>'sub'));

create policy "profile_insert_own"
on public.profiles for insert to authenticated
with check (id = (select auth.jwt()->>'sub'));

create policy "profile_update_own"
on public.profiles for update to authenticated
using (id = (select auth.jwt()->>'sub'))
with check (id = (select auth.jwt()->>'sub'));

-- =====================================================
-- 25. LOGIN HISTORY POLICIES
-- Login events are written by trusted server functions.
-- =====================================================

create policy "login_select_own"
on public.login_events for select to authenticated
using (user_id = (select auth.jwt()->>'sub'));

-- =====================================================
-- 26. CONVERSATION POLICIES
-- =====================================================

create policy "conversation_select_own"
on public.conversations for select to authenticated
using (user_id = (select auth.jwt()->>'sub'));

create policy "conversation_insert_own"
on public.conversations for insert to authenticated
with check (user_id = (select auth.jwt()->>'sub'));

create policy "conversation_update_own"
on public.conversations for update to authenticated
using (user_id = (select auth.jwt()->>'sub'))
with check (user_id = (select auth.jwt()->>'sub'));

create policy "conversation_delete_own"
on public.conversations for delete to authenticated
using (user_id = (select auth.jwt()->>'sub'));

-- =====================================================
-- 27. MESSAGE POLICIES
-- Client can insert only its own user messages.
-- Assistant messages are inserted by trusted backend.
-- =====================================================

create policy "message_select_own"
on public.messages for select to authenticated
using (user_id = (select auth.jwt()->>'sub'));

create policy "message_insert_user"
on public.messages for insert to authenticated
with check (
    user_id = (select auth.jwt()->>'sub')
    and role = 'user'
    and exists (
        select 1
        from public.conversations c
        where c.id = conversation_id
        and c.user_id = (select auth.jwt()->>'sub')
    )
);

create policy "message_delete_own"
on public.messages for delete to authenticated
using (user_id = (select auth.jwt()->>'sub'));

-- =====================================================
-- 28. AI USAGE POLICIES
-- =====================================================

create policy "usage_select_own"
on public.ai_usage_events for select to authenticated
using (user_id = (select auth.jwt()->>'sub'));

-- =====================================================
-- 29. PROVIDER SETTINGS POLICIES
-- Store status only, never raw API keys.
-- =====================================================

create policy "provider_select_own"
on public.user_provider_settings for select to authenticated
using (user_id = (select auth.jwt()->>'sub'));

create policy "provider_insert_own"
on public.user_provider_settings for insert to authenticated
with check (user_id = (select auth.jwt()->>'sub'));

create policy "provider_update_own"
on public.user_provider_settings for update to authenticated
using (user_id = (select auth.jwt()->>'sub'))
with check (user_id = (select auth.jwt()->>'sub'));

create policy "provider_delete_own"
on public.user_provider_settings for delete to authenticated
using (user_id = (select auth.jwt()->>'sub'));

-- =====================================================
-- 30. USER PREFERENCES POLICIES
-- =====================================================

create policy "preferences_select_own"
on public.user_preferences for select to authenticated
using (user_id = (select auth.jwt()->>'sub'));

create policy "preferences_insert_own"
on public.user_preferences for insert to authenticated
with check (user_id = (select auth.jwt()->>'sub'));

create policy "preferences_update_own"
on public.user_preferences for update to authenticated
using (user_id = (select auth.jwt()->>'sub'))
with check (user_id = (select auth.jwt()->>'sub'));

-- =====================================================
-- 31. SUPPORT TICKET POLICIES
-- Users can create and read tickets.
-- Admin updates only through protected server functions.
-- =====================================================

create policy "ticket_select_own"
on public.support_tickets for select to authenticated
using (user_id = (select auth.jwt()->>'sub'));

create policy "ticket_insert_own"
on public.support_tickets for insert to authenticated
with check (user_id = (select auth.jwt()->>'sub'));

-- =====================================================
-- 32. SUPPORT MESSAGE POLICIES
-- =====================================================

create policy "support_message_select_own"
on public.support_messages for select to authenticated
using (
    exists (
        select 1 from public.support_tickets t
        where t.id = ticket_id
        and t.user_id = (select auth.jwt()->>'sub')
    )
);

create policy "support_message_insert_own"
on public.support_messages for insert to authenticated
with check (
    sender_type = 'user'
    and exists (
        select 1 from public.support_tickets t
        where t.id = ticket_id
        and t.user_id = (select auth.jwt()->>'sub')
    )
);

-- =====================================================
-- 33. APP ACTIVITY POLICIES
-- =====================================================

create policy "events_select_own"
on public.app_events for select to authenticated
using (user_id = (select auth.jwt()->>'sub'));

create policy "events_insert_own"
on public.app_events for insert to authenticated
with check (user_id = (select auth.jwt()->>'sub'));

-- =====================================================
-- 34. ANNOUNCEMENTS
-- =====================================================

create policy "announcement_read_published"
on public.announcements for select to authenticated
using (is_published = true);

-- =====================================================
-- 35. PUBLIC APP CONFIG
-- Only non-secret configuration can be read.
-- =====================================================

create policy "config_read_authenticated"
on public.app_config for select to authenticated
using (true);

-- =====================================================
-- 36. GRANTS
-- =====================================================

revoke all on all tables in schema public
from anon, authenticated;

grant usage on schema public to authenticated;

grant select, insert
on public.profiles to authenticated;

grant update(display_name, avatar_url)
on public.profiles to authenticated;

grant select
on public.login_events to authenticated;

grant select, insert, update, delete
on public.conversations to authenticated;

grant select, insert, delete
on public.messages to authenticated;

grant select
on public.ai_usage_events to authenticated;

grant select, insert, update, delete
on public.user_provider_settings to authenticated;

grant select, insert, update
on public.user_preferences to authenticated;

grant select, insert
on public.support_tickets to authenticated;

grant select, insert
on public.support_messages to authenticated;

grant select, insert
on public.app_events to authenticated;

grant select
on public.announcements, public.app_config
to authenticated;

-- Trusted server-side access
grant all privileges on all tables in schema public
to service_role;

-- =====================================================
-- 37. PROTECT PRIVATE ADMIN SCHEMA
-- =====================================================

revoke all on schema private
from public, anon, authenticated;

revoke all on all tables in schema private
from public, anon, authenticated;

revoke all on all sequences in schema private
from public, anon, authenticated;

-- =====================================================
-- 38. ADMIN TABLE ACCESS
-- Service role only.
-- =====================================================

grant usage on schema private to service_role;

grant all privileges on all tables in schema private
to service_role;

-- =====================================================
-- 39. FINAL STATUS
-- =====================================================

select
    'BADSHAH AI DATABASE SETUP COMPLETED' as status,
    now() as completed_at;
