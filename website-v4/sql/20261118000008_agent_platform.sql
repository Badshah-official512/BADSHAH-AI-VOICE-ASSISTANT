-- BADSHAH-RANI agent platform: Supabase side. Safe to re-run.
-- Users log in with Firebase, so every table keys on firebase_uid (text). RLS is ON with NO public policies:
-- only the backend (service-role key, server-side) can read/write. Never ship the service-role key to the browser.

create table if not exists public.agent_workspaces (
  id uuid primary key default gen_random_uuid(),
  firebase_uid text not null unique,
  container_name text, volume_name text, novnc_port int,
  status text not null default 'none' check (status in ('none','creating','running','stopped','error','deleted')),
  cpu_limit numeric default 1, mem_limit_mb int default 1024, disk_limit_mb int default 2048,
  last_started_at timestamptz, created_at timestamptz default now(), updated_at timestamptz default now()
);
create table if not exists public.agent_tasks (
  id uuid primary key default gen_random_uuid(),
  firebase_uid text not null, parent_id uuid references public.agent_tasks(id) on delete cascade,
  agent text not null check (agent in ('manager','browser','computer','phone','youtube','research','coding','email','automation','file','connector')),
  goal text not null, status text not null default 'queued' check (status in ('queued','running','waiting_approval','done','failed','cancelled')),
  progress int default 0, attempts int default 0, max_attempts int default 3,
  result jsonb, error text, run_at timestamptz default now(),
  created_at timestamptz default now(), updated_at timestamptz default now()
);
create index if not exists agent_tasks_user_idx on public.agent_tasks (firebase_uid, created_at desc);
create index if not exists agent_tasks_queue_idx on public.agent_tasks (status, run_at);
create table if not exists public.agent_task_logs (
  id bigserial primary key, task_id uuid not null references public.agent_tasks(id) on delete cascade,
  level text default 'info', message text not null, created_at timestamptz default now()
);
create table if not exists public.agent_approvals (
  id uuid primary key default gen_random_uuid(), task_id uuid not null references public.agent_tasks(id) on delete cascade,
  firebase_uid text not null, action text not null, detail jsonb,
  status text not null default 'pending' check (status in ('pending','approved','denied')),
  created_at timestamptz default now(), decided_at timestamptz
);
create table if not exists public.connectors (
  id uuid primary key default gen_random_uuid(), firebase_uid text not null,
  service text not null, auth_type text not null check (auth_type in ('oauth2','api_key','webhook','mcp','custom')),
  scopes text[] default '{}', enabled_for_agents boolean default true,
  credentials_enc text,            -- ENCRYPTED by the backend (AES-GCM, key in server env). Never plaintext.
  status text default 'connected', created_at timestamptz default now(), unique (firebase_uid, service)
);
create table if not exists public.phone_devices (
  id uuid primary key default gen_random_uuid(), firebase_uid text not null,
  device_name text, pair_code text, paired boolean default false, last_seen timestamptz,
  control_enabled boolean default false, created_at timestamptz default now()
);
create table if not exists public.audit_logs (
  id bigserial primary key, firebase_uid text, actor text, action text not null, detail jsonb, ip text, created_at timestamptz default now()
);
create index if not exists audit_logs_user_idx on public.audit_logs (firebase_uid, created_at desc);

do $$ declare t text; begin
  foreach t in array array['agent_workspaces','agent_tasks','agent_task_logs','agent_approvals','connectors','phone_devices','audit_logs'] loop
    execute format('alter table public.%I enable row level security', t);
  end loop; end $$;
-- Realtime (optional, for live task progress in the dashboard): add agent_tasks to the supabase_realtime publication in the Supabase UI.
