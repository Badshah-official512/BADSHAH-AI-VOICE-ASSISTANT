-- BADSHAH AI — Migration 005 (API KEY POOL) — idempotent.
-- Owner API keys (Gemini / Groq / ElevenLabs) live here so users need no key of their own.
-- The Worker reads keys with the service role and rotates to the next active key on errors.
-- RLS is ON with NO public policies: browsers can never read this table.
-- Keys are added/rotated from the Admin Panel (API Keys tab) or by the owner in SQL.
-- NEVER commit real key values into this file or the repo.

create table if not exists public.api_keys (
  id uuid primary key default gen_random_uuid(),
  provider text not null check (provider in ('gemini','groq','elevenlabs')),
  key_value text not null,
  label text,
  active boolean not null default true,
  use_count integer not null default 0,
  last_used_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists api_keys_provider_active_idx on public.api_keys (provider, active);
alter table public.api_keys enable row level security;

select 'BADSHAH AI MIGRATION 005 (API KEYS) COMPLETED' as result, now() as at;
