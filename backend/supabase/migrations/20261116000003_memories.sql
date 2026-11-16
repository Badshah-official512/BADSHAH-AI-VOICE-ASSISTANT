-- ============================================================
-- BADSHAH-RANI AI — Migration 003: long-term memories
-- The assistant may save facts the user approves ("ye yaad rakho…")
-- and read them back in later conversations. Idempotent.
-- ============================================================

create table if not exists public.memories (
    id uuid primary key default gen_random_uuid(),
    user_id text not null,
    kind text not null default 'note',
    text text not null,
    created_at timestamptz not null default now()
);

create index if not exists memories_user_created_idx
    on public.memories (user_id, created_at desc);

alter table public.memories enable row level security;

-- Backend-only access (the ai-api / admin-api functions use the service role).
drop policy if exists "memories service role" on public.memories;
create policy "memories service role" on public.memories
    for all using (auth.role() = 'service_role')
    with check (auth.role() = 'service_role');

select 'BADSHAH AI MIGRATION 003 COMPLETED' as result, now() as completed_at;
