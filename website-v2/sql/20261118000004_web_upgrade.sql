-- BADSHAH AI — Migration 004 (WEB UPGRADE) — idempotent, additive only.
-- Run in Supabase SQL Editor (project aghlacmqlagmigoznxlm). Safe to re-run.
-- Adds the columns/tables the upgraded website + worker + admin panel use.
-- Nothing is renamed or dropped; existing Android-app data is untouched.

-- 1) profiles: link Firebase account + plan fields the web uses
alter table public.profiles add column if not exists firebase_uid text;
alter table public.profiles add column if not exists plan text not null default 'free';
alter table public.profiles add column if not exists plan_expires_at timestamptz;
alter table public.profiles add column if not exists blocked boolean not null default false;
create unique index if not exists profiles_firebase_uid_key on public.profiles (firebase_uid) where firebase_uid is not null;

-- 2) payment_requests: web paid-request fields
alter table public.payment_requests add column if not exists firebase_uid text;
alter table public.payment_requests add column if not exists user_email text;
alter table public.payment_requests add column if not exists months integer;
alter table public.payment_requests add column if not exists amount integer;
alter table public.payment_requests add column if not exists sender_name text;
alter table public.payment_requests add column if not exists sender_number text;
alter table public.payment_requests add column if not exists status text not null default 'pending';

-- 3) support_tickets: web help-center fields
alter table public.support_tickets add column if not exists firebase_uid text;
alter table public.support_tickets add column if not exists user_email text;
alter table public.support_tickets add column if not exists subject text;
alter table public.support_tickets add column if not exists admin_reply text;
alter table public.support_tickets add column if not exists status text not null default 'open';

-- 4) computer pairings (web "Connect Computer")
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

-- 5) announcements (admin panel → app)
create table if not exists public.announcements (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  body text,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

select 'BADSHAH AI MIGRATION 004 (WEB) COMPLETED' as result, now() as at;
