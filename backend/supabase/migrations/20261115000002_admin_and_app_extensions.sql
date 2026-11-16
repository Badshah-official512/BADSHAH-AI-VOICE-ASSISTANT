-- =====================================================
-- BADSHAH AI - MIGRATION 002
-- Extends 20261115000001_core_schema.sql (does not repeat it).
--
-- Why this exists:
--  * Supabase's REST API does not expose the `private` schema, so
--    Edge Functions cannot touch admin_sessions / attempts / audit
--    directly. Service-role-only wrapper functions fix that.
--  * Admin analytics are computed in SQL (no private chat content).
--  * The Android app needs safe, validated ways to record logins and
--    AI usage (table grants only allow SELECT on those tables).
--  * Blocked users must actually be blocked by the database.
--  * Missing tables: voice_profiles, email_outbox.
-- Idempotent: safe to run more than once.
-- =====================================================

-- ---------- helpers ----------
create or replace function public.touch_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin new.updated_at = now(); return new; end; $$;

-- ---------- NEW TABLES ----------
create table if not exists public.voice_profiles (
    id text primary key,
    label text not null,
    category text not null default 'natural',
    language text not null default 'en',
    engine text not null default 'android_tts',
    pitch numeric not null default 1.0 check (pitch between 0.5 and 2.0),
    speed numeric not null default 1.0 check (speed between 0.5 and 2.0),
    is_enabled boolean not null default true,
    sort_order integer not null default 100,
    notes text,
    updated_at timestamptz not null default now()
);

create table if not exists public.email_outbox (
    id uuid primary key default gen_random_uuid(),
    ticket_id uuid references public.support_tickets(id) on delete set null,
    to_email text not null,
    subject text not null,
    body text not null,
    status text not null default 'pending' check (status in ('pending','sent','failed','skipped')),
    error text,
    created_at timestamptz not null default now(),
    sent_at timestamptz
);

alter table public.voice_profiles enable row level security;
alter table public.email_outbox  enable row level security;

drop policy if exists "voice_profiles_read_enabled" on public.voice_profiles;
create policy "voice_profiles_read_enabled" on public.voice_profiles
    for select to authenticated using (is_enabled = true);
-- email_outbox: RLS on, NO policies => unreachable except service_role.

revoke all on public.voice_profiles, public.email_outbox from anon, authenticated;
grant select on public.voice_profiles to authenticated;
grant all on public.voice_profiles, public.email_outbox to service_role;

create index if not exists idx_login_created   on public.login_events(created_at desc);
create index if not exists idx_usage_created   on public.ai_usage_events(created_at desc);
create index if not exists idx_tickets_status  on public.support_tickets(status, updated_at desc);
create index if not exists idx_support_msgs    on public.support_messages(ticket_id, created_at);
create index if not exists idx_outbox_status   on public.email_outbox(status, created_at);

drop trigger if exists trg_tickets_touch on public.support_tickets;
create trigger trg_tickets_touch before update on public.support_tickets
    for each row execute function public.touch_updated_at();

-- A new support message (user or admin) bumps the ticket.
create or replace function public.bump_ticket_on_message()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    update public.support_tickets set updated_at = now() where id = new.ticket_id;
    return new;
end; $$;
drop trigger if exists trg_support_msg_bump on public.support_messages;
create trigger trg_support_msg_bump after insert on public.support_messages
    for each row execute function public.bump_ticket_on_message();

-- ---------- SEED NON-SECRET CONFIG ----------
insert into public.app_config (config_key, config_value) values
  ('maintenance', '{"enabled": false, "message": ""}'),
  ('min_app_version', '{"android": 1}'),
  ('social_links', '{}')
on conflict (config_key) do nothing;

-- =====================================================
-- BLOCKED USERS: enforced by the database, not only the UI
-- =====================================================
create or replace function public.is_blocked_user()
returns boolean language sql stable security definer set search_path = '' as $$
    select coalesce(
        (select p.is_blocked from public.profiles p
         where p.id = (select auth.jwt()->>'sub')), false);
$$;
revoke all on function public.is_blocked_user() from public, anon;
grant execute on function public.is_blocked_user() to authenticated, service_role;

do $$
declare t text; c text;
begin
  foreach t in array array['conversations','messages','support_tickets','support_messages',
                           'user_preferences','user_provider_settings','app_events'] loop
    foreach c in array array['insert','update'] loop
      if c = 'update' and t in ('messages','support_tickets','support_messages','app_events') then
        continue; -- those tables have no UPDATE grant for clients
      end if;
      execute format('drop policy if exists %I on public.%I', 'blocked_users_no_'||c, t);
      if c = 'insert' then
        execute format('create policy %I on public.%I as restrictive for insert to authenticated with check (not public.is_blocked_user())', 'blocked_users_no_'||c, t);
      else
        execute format('create policy %I on public.%I as restrictive for update to authenticated using (not public.is_blocked_user())', 'blocked_users_no_'||c, t);
      end if;
    end loop;
  end loop;
end $$;

-- The app talks to Gemini/Groq directly with the user's own key, so the
-- client itself must be able to save assistant replies to its own chat.
drop policy if exists "messages_insert_own_chat" on public.messages;
create policy "messages_insert_own_chat" on public.messages
    for insert to authenticated
    with check (
        user_id = (select auth.jwt()->>'sub')
        and role in ('user','assistant')
        and exists (select 1 from public.conversations c
                    where c.id = conversation_id
                      and c.user_id = (select auth.jwt()->>'sub'))
    );

-- =====================================================
-- APP-FACING RPCs (authenticated users, own data only)
-- =====================================================
create or replace function public.app_touch_profile(
    p_email text, p_display_name text, p_avatar_url text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare uid text := (select auth.jwt()->>'sub'); blocked boolean;
begin
    if uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;
    insert into public.profiles (id, email, display_name, avatar_url, last_seen_at)
    values (uid, left(p_email,320), left(p_display_name,120), left(p_avatar_url,1000), now())
    on conflict (id) do update set
        email = coalesce(excluded.email, public.profiles.email),
        display_name = coalesce(public.profiles.display_name, excluded.display_name),
        avatar_url = coalesce(public.profiles.avatar_url, excluded.avatar_url),
        last_seen_at = now(), updated_at = now();
    select is_blocked into blocked from public.profiles where id = uid;
    return jsonb_build_object('user_id', uid, 'is_blocked', blocked);
end; $$;

create or replace function public.app_record_login(
    p_provider text, p_event_type text default 'login', p_device text default 'android')
returns void language plpgsql security definer set search_path = '' as $$
declare uid text := (select auth.jwt()->>'sub');
begin
    if uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;
    if p_event_type not in ('login','register','logout') then
        raise exception 'bad event_type'; end if;
    if not exists (select 1 from public.profiles where id = uid) then
        raise exception 'profile missing; call app_touch_profile first'; end if;
    insert into public.login_events (user_id, provider, event_type, success, device_type)
    values (uid, left(p_provider,40), p_event_type, true, left(p_device,40));
end; $$;

create or replace function public.app_record_usage(
    p_provider text, p_model text, p_event_type text, p_success boolean,
    p_input_tokens bigint default 0, p_output_tokens bigint default 0,
    p_latency_ms integer default null, p_error_code text default null)
returns void language plpgsql security definer set search_path = '' as $$
declare uid text := (select auth.jwt()->>'sub');
begin
    if uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;
    if p_provider not in ('gemini','groq','elevenlabs','android_tts') then
        raise exception 'unknown provider'; end if;
    if public.is_blocked_user() then raise exception 'account blocked'; end if;
    insert into public.ai_usage_events
      (user_id, provider, model_name, event_type, success, input_tokens, output_tokens, latency_ms, error_code)
    values (uid, p_provider, left(p_model,120), left(coalesce(p_event_type,'chat'),40), coalesce(p_success,false),
            greatest(coalesce(p_input_tokens,0),0), greatest(coalesce(p_output_tokens,0),0),
            p_latency_ms, left(p_error_code,80));
end; $$;

revoke all on function public.app_touch_profile(text,text,text),
                       public.app_record_login(text,text,text),
                       public.app_record_usage(text,text,text,boolean,bigint,bigint,integer,text)
    from public, anon;
grant execute on function public.app_touch_profile(text,text,text),
                          public.app_record_login(text,text,text),
                          public.app_record_usage(text,text,text,boolean,bigint,bigint,integer,text)
    to authenticated;

-- =====================================================
-- ADMIN WRAPPERS (service_role only) over private.* tables
-- =====================================================
create or replace function public.admin_rate_limit_ok(p_ip_hash text,
    p_window_minutes int default 15, p_max_per_ip int default 5, p_max_global int default 40)
returns boolean language sql stable security definer set search_path = '' as $$
    select
      (select count(*) from private.admin_login_attempts
        where success = false and ip_hash = p_ip_hash
          and attempted_at > now() - make_interval(mins => p_window_minutes)) < p_max_per_ip
      and
      (select count(*) from private.admin_login_attempts
        where success = false
          and attempted_at > now() - make_interval(mins => p_window_minutes)) < p_max_global;
$$;

create or replace function public.admin_record_attempt(p_ip_hash text, p_success boolean)
returns void language plpgsql security definer set search_path = '' as $$
begin
    insert into private.admin_login_attempts (ip_hash, success) values (p_ip_hash, p_success);
    delete from private.admin_login_attempts where attempted_at < now() - interval '90 days';
end; $$;

create or replace function public.admin_create_session(p_token_hash text, p_ttl_minutes int default 120)
returns timestamptz language plpgsql security definer set search_path = '' as $$
declare exp timestamptz := now() + make_interval(mins => p_ttl_minutes);
begin
    insert into private.admin_sessions (token_hash, expires_at, last_seen_at)
    values (p_token_hash, exp, now());
    delete from private.admin_sessions where expires_at < now() - interval '7 days';
    return exp;
end; $$;

-- Returns session id if valid (not revoked, not expired, not idle too long).
create or replace function public.admin_validate_session(p_token_hash text, p_idle_minutes int default 30)
returns uuid language plpgsql security definer set search_path = '' as $$
declare sid uuid;
begin
    select id into sid from private.admin_sessions
     where token_hash = p_token_hash and revoked_at is null and expires_at > now()
       and coalesce(last_seen_at, created_at) > now() - make_interval(mins => p_idle_minutes);
    if sid is not null then
        update private.admin_sessions set last_seen_at = now() where id = sid;
    end if;
    return sid;
end; $$;

create or replace function public.admin_revoke_session(p_token_hash text default null, p_session_id uuid default null)
returns integer language plpgsql security definer set search_path = '' as $$
declare n integer;
begin
    update private.admin_sessions set revoked_at = now()
     where revoked_at is null
       and ((p_token_hash is not null and token_hash = p_token_hash)
         or (p_session_id is not null and id = p_session_id));
    get diagnostics n = row_count; return n;
end; $$;

create or replace function public.admin_revoke_all_sessions(p_except_token_hash text default null)
returns integer language plpgsql security definer set search_path = '' as $$
declare n integer;
begin
    update private.admin_sessions set revoked_at = now()
     where revoked_at is null and (p_except_token_hash is null or token_hash <> p_except_token_hash);
    get diagnostics n = row_count; return n;
end; $$;

-- Never returns token hashes.
create or replace function public.admin_list_sessions(p_limit int default 50)
returns jsonb language sql stable security definer set search_path = '' as $$
    select coalesce(jsonb_agg(to_jsonb(s) order by s.created_at desc), '[]'::jsonb) from (
        select id, created_at, expires_at, last_seen_at, revoked_at,
               (revoked_at is null and expires_at > now()) as is_active
        from private.admin_sessions order by created_at desc limit least(p_limit,200)) s;
$$;

create or replace function public.admin_list_attempts(p_limit int default 100)
returns jsonb language sql stable security definer set search_path = '' as $$
    select coalesce(jsonb_agg(to_jsonb(a) order by a.attempted_at desc), '[]'::jsonb) from (
        select id, left(ip_hash, 10) as ip_ref, success, attempted_at
        from private.admin_login_attempts order by attempted_at desc limit least(p_limit,500)) a;
$$;

create or replace function public.admin_write_audit(p_action text, p_target_user_id text default null,
                                                    p_details jsonb default '{}'::jsonb)
returns void language sql security definer set search_path = '' as $$
    insert into private.admin_audit_logs (action, target_user_id, details)
    values (left(p_action,80), left(p_target_user_id,200), coalesce(p_details,'{}'::jsonb));
$$;

create or replace function public.admin_list_audit(p_limit int default 100)
returns jsonb language sql stable security definer set search_path = '' as $$
    select coalesce(jsonb_agg(to_jsonb(a) order by a.created_at desc), '[]'::jsonb) from (
        select id, action, target_user_id, details, created_at
        from private.admin_audit_logs order by created_at desc limit least(p_limit,500)) a;
$$;

-- =====================================================
-- ADMIN ANALYTICS (aggregates only; no chat content)
-- =====================================================
create or replace function public.admin_overview()
returns jsonb language sql stable security definer set search_path = '' as $$
select jsonb_build_object(
  'total_users',      (select count(*) from public.profiles),
  'blocked_users',    (select count(*) from public.profiles where is_blocked),
  'active_24h',       (select count(*) from public.profiles where last_seen_at > now() - interval '1 day'),
  'active_7d',        (select count(*) from public.profiles where last_seen_at > now() - interval '7 days'),
  'active_30d',       (select count(*) from public.profiles where last_seen_at > now() - interval '30 days'),
  'new_users_24h',    (select count(*) from public.profiles where created_at > now() - interval '1 day'),
  'new_users_7d',     (select count(*) from public.profiles where created_at > now() - interval '7 days'),
  'new_users_30d',    (select count(*) from public.profiles where created_at > now() - interval '30 days'),
  'ai_requests_total',(select count(*) from public.ai_usage_events where event_type = 'chat'),
  'ai_requests_24h',  (select count(*) from public.ai_usage_events where event_type = 'chat' and created_at > now() - interval '1 day'),
  'ai_requests_7d',   (select count(*) from public.ai_usage_events where event_type = 'chat' and created_at > now() - interval '7 days'),
  'ai_requests_30d',  (select count(*) from public.ai_usage_events where event_type = 'chat' and created_at > now() - interval '30 days'),
  'conversations',    (select count(*) from public.conversations),
  'messages',         (select count(*) from public.messages),
  'open_tickets',     (select count(*) from public.support_tickets where status in ('open','in_progress')),
  'by_provider',      (select coalesce(jsonb_object_agg(provider, c), '{}'::jsonb)
                         from (select provider, count(*) c from public.ai_usage_events
                               where event_type = 'chat' group by provider) t),
  'daily', (select coalesce(jsonb_agg(to_jsonb(x) order by x.day), '[]'::jsonb) from (
      select d::date as day,
        (select count(*) from public.ai_usage_events u where u.event_type='chat' and u.created_at::date = d::date) as requests,
        (select count(*) from public.profiles p where p.created_at::date = d::date) as signups,
        (select count(distinct l.user_id) from public.login_events l where l.created_at::date = d::date) as logins
      from generate_series(current_date - 29, current_date, interval '1 day') d) x)
);
$$;

create or replace function public.admin_login_analytics(p_days int default 30)
returns jsonb language sql stable security definer set search_path = '' as $$
with e as (select * from public.login_events where created_at > now() - make_interval(days => least(greatest(p_days,1),365)))
select jsonb_build_object(
  'successful_logins', (select count(*) from e where event_type='login' and success),
  'failed_logins',     (select count(*) from e where not success),
  'registrations',     (select count(*) from e where event_type='register'),
  'by_provider',       (select coalesce(jsonb_object_agg(coalesce(provider,'unknown'), c), '{}'::jsonb)
                          from (select provider, count(*) c from e group by provider) t),
  'recent', (select coalesce(jsonb_agg(to_jsonb(r) order by r.created_at desc), '[]'::jsonb) from (
      select e.id, e.user_id, p.email, e.provider, e.event_type, e.success, e.device_type, e.created_at
      from e left join public.profiles p on p.id = e.user_id
      order by e.created_at desc limit 200) r),
  'failed_logins_note', 'Firebase rejects bad credentials before Supabase sees them; only app-reported failures appear here.'
);
$$;

create or replace function public.admin_api_analytics(p_days int default 30)
returns jsonb language sql stable security definer set search_path = '' as $$
with u as (select * from public.ai_usage_events where created_at > now() - make_interval(days => least(greatest(p_days,1),365)))
select jsonb_build_object(
  'totals', (select jsonb_build_object('requests', count(*), 'success', count(*) filter (where success),
                'failures', count(*) filter (where not success),
                'avg_latency_ms', round(avg(latency_ms))) from u),
  'by_model', (select coalesce(jsonb_agg(to_jsonb(m) order by m.requests desc), '[]'::jsonb) from (
      select provider, coalesce(model_name,'unknown') as model, count(*) as requests,
             count(*) filter (where success) as success, count(*) filter (where not success) as failures,
             round(avg(latency_ms)) as avg_latency_ms,
             coalesce(sum(input_tokens),0) as input_tokens, coalesce(sum(output_tokens),0) as output_tokens
      from u group by provider, model_name) m),
  'top_errors', (select coalesce(jsonb_agg(to_jsonb(x) order by x.n desc), '[]'::jsonb) from (
      select provider, error_code, count(*) n from u where not success and error_code is not null
      group by provider, error_code order by count(*) desc limit 10) x),
  'daily', (select coalesce(jsonb_agg(to_jsonb(d) order by d.day), '[]'::jsonb) from (
      select created_at::date as day, provider, count(*) as requests from u group by 1,2) d)
);
$$;

create or replace function public.admin_list_users(
    p_search text default null, p_status text default 'all',
    p_limit int default 25, p_offset int default 0)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare total bigint; rows jsonb; s text := lower(nullif(trim(p_search),''));
begin
    select count(*) into total from public.profiles p
     where (s is null or position(s in lower(coalesce(p.email,''))) > 0
                      or position(s in lower(coalesce(p.display_name,''))) > 0
                      or position(s in lower(p.id)) > 0)
       and (p_status = 'all' or (p_status = 'blocked' and p.is_blocked) or (p_status = 'active' and not p.is_blocked));
    select coalesce(jsonb_agg(to_jsonb(r)), '[]'::jsonb) into rows from (
        select p.id, p.email, p.display_name, p.is_blocked, p.created_at, p.last_seen_at,
               (select max(l.created_at) from public.login_events l where l.user_id = p.id and l.success and l.event_type='login') as last_login_at,
               (select l.provider from public.login_events l where l.user_id = p.id order by l.created_at desc limit 1) as login_provider,
               pr.selected_provider, pr.selected_model, pr.selected_voice, pr.voice_engine, pr.speech_rate, pr.speech_pitch, pr.language,
               coalesce((select bool_or(s2.is_configured) from public.user_provider_settings s2 where s2.user_id = p.id), false) as api_configured
        from public.profiles p left join public.user_preferences pr on pr.user_id = p.id
        where (s is null or position(s in lower(coalesce(p.email,''))) > 0
                         or position(s in lower(coalesce(p.display_name,''))) > 0
                         or position(s in lower(p.id)) > 0)
          and (p_status = 'all' or (p_status = 'blocked' and p.is_blocked) or (p_status = 'active' and not p.is_blocked))
        order by p.created_at desc
        limit least(greatest(p_limit,1),100) offset greatest(p_offset,0)) r;
    return jsonb_build_object('total', total, 'rows', rows);
end; $$;

-- Detail: counts and provider STATUS only. No keys, no chat text.
create or replace function public.admin_user_detail(p_user_id text)
returns jsonb language sql stable security definer set search_path = '' as $$
select jsonb_build_object(
  'profile',     (select to_jsonb(p) from public.profiles p where p.id = p_user_id),
  'preferences', (select to_jsonb(x) from public.user_preferences x where x.user_id = p_user_id),
  'providers',   (select coalesce(jsonb_agg(jsonb_build_object('provider', provider, 'is_configured', is_configured,
                         'connection_status', connection_status, 'last_tested_at', last_tested_at)), '[]'::jsonb)
                    from public.user_provider_settings where user_id = p_user_id),
  'counts', jsonb_build_object(
      'conversations', (select count(*) from public.conversations where user_id = p_user_id),
      'messages',      (select count(*) from public.messages where user_id = p_user_id),
      'ai_requests',   (select count(*) from public.ai_usage_events where user_id = p_user_id),
      'tickets',       (select count(*) from public.support_tickets where user_id = p_user_id)),
  'recent_logins', (select coalesce(jsonb_agg(to_jsonb(l)), '[]'::jsonb) from (
      select provider, event_type, success, device_type, created_at from public.login_events
      where user_id = p_user_id order by created_at desc limit 10) l)
);
$$;

-- Lock every admin_* function to service_role.
do $$
declare f record;
begin
  for f in select p.oid::regprocedure as sig from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname like 'admin\_%' escape '\' loop
    execute format('revoke all on function %s from public, anon, authenticated', f.sig);
    execute format('grant execute on function %s to service_role', f.sig);
  end loop;
end $$;

select 'BADSHAH AI MIGRATION 002 COMPLETED' as status, now() as completed_at;
