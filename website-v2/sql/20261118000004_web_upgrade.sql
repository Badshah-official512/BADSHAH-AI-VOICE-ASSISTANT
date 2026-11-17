-- BADSHAH AI — Migration 004 v2 (WEB UPGRADE) — idempotent, additive only.
-- Run in Supabase SQL Editor (project aghlacmqlagmigoznxlm). Safe to re-run.
-- NOTE: v1 of this migration failed because public.payment_requests does not
-- exist in this project (and v3 removed the paid system anyway, so that table
-- is not needed). This v2 only touches tables that exist + creates pairings.

-- 1) profiles: link Firebase account + block flag the web/admin use
alter table public.profiles add column if not exists firebase_uid text;
alter table public.profiles add column if not exists blocked boolean not null default false;
create unique index if not exists profiles_firebase_uid_key on public.profiles (firebase_uid) where firebase_uid is not null;

-- 2) support_tickets: web help-center fields
alter table public.support_tickets add column if not exists firebase_uid text;
alter table public.support_tickets add column if not exists user_email text;
alter table public.support_tickets add column if not exists subject text;
alter table public.support_tickets add column if not exists admin_reply text;
alter table public.support_tickets add column if not exists status text not null default 'open';

-- 3) computer pairings (web "Connect Computer")
create table if not exists public.computer_pairings (
  id uuid primary key default gen_random_uuid(),
  code text not null,
  firebase_uid text,
  status text not null default 'waiting',
  computer_name text,
  created_at timestamptz not null default now(),
  paired_at timestamptz
);
create index if not exists computer_pairings_code_idx on public.computer_pairings (code);

select 'BADSHAH AI MIGRATION 004 v2 (WEB) COMPLETED' as result, now() as at;
