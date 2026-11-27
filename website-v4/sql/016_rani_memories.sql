-- ============================================================
-- BADSHAH-RANI AI — Migration 016: RANI long-term memory
-- v1.9.0 §21.2 — ONE new table `rani_memories` for RANI's persistent
-- memory (conversations, summaries, facts), synced from the app's
-- on-device encrypted store (the on-device store is the source of
-- truth; sync is best-effort and the app works fully with sync off
-- or failing).
--
-- RUN: Supabase Dashboard → SQL Editor → paste → Run (once only).
-- PREPARE ONLY — do NOT run this file yourself; the admin (coordinator) will run it.
-- Idempotent: running it again is safe.
--
-- What it does:
--  1. Creates `rani_memories` (id, user_id, app default 'rani', kind
--     conversation/summary/fact, title, content, facts jsonb, source
--     live_call/chat, created_at, updated_at).
--  2. Owner-only RLS (auth.uid() = user_id), same pattern as the
--     existing RANI tables (migration 015).
--  3. Indexes for the recall lookups: (user_id, kind, created_at DESC)
--     and (user_id, title) for fact lookup.
--
-- PRIVACY (v1.9.0 change, stated explicitly in §21.2):
--  * §20.5 said Supabase gets counts/metadata only. v1.9.0 CHANGES that
--    rule FOR MEMORY ONLY, because the boss ordered her to remember:
--    transcript/summary/facts now sync to the user's OWN rows
--    (owner-only RLS, same project, same account that already holds
--    his chats in conversations/messages). Nothing else in §20.5
--    changes: contacts, screen text, camera frames, voice-prints,
--    face-prints, screen recordings and the Gemini key NEVER leave
--    the phone. A visible switch in Settings → Memory ("Sync memories
--    to my account", default ON when signed in) turns sync off, and
--    "Forget everything" deletes the user's rani_memories rows.
-- ============================================================

-- ---------- 1. rani_memories ----------
create table if not exists public.rani_memories (
    id uuid primary key default gen_random_uuid(),
    user_id text not null,
    app text not null default 'rani',
    kind text not null check (kind in ('conversation', 'summary', 'fact')),
    title text not null default '',
    content text not null default '',
    facts jsonb not null default '{}',
    source text not null check (source in ('live_call', 'chat')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.rani_memories enable row level security;

drop policy if exists "rani_memories_select_own" on public.rani_memories;
create policy "rani_memories_select_own" on public.rani_memories for select to authenticated
    using (user_id = (select auth.jwt()->>'sub'));

drop policy if exists "rani_memories_insert_own" on public.rani_memories;
create policy "rani_memories_insert_own" on public.rani_memories for insert to authenticated
    with check (user_id = (select auth.jwt()->>'sub'));

drop policy if exists "rani_memories_update_own" on public.rani_memories;
create policy "rani_memories_update_own" on public.rani_memories for update to authenticated
    using (user_id = (select auth.jwt()->>'sub'))
    with check (user_id = (select auth.jwt()->>'sub'));

drop policy if exists "rani_memories_delete_own" on public.rani_memories;
create policy "rani_memories_delete_own" on public.rani_memories for delete to authenticated
    using (user_id = (select auth.jwt()->>'sub'));

revoke all on public.rani_memories from anon, authenticated;
grant select, insert, update, delete on public.rani_memories to authenticated;
grant all on public.rani_memories to service_role;

-- ---------- 2. Indexes ----------
create index if not exists idx_rani_memories_user_kind_created
    on public.rani_memories (user_id, kind, created_at desc);
create index if not exists idx_rani_memories_user_title
    on public.rani_memories (user_id, title);
