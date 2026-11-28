-- 017_rani_memory_visual.sql — RANI v3.0.0 (§23.7)
-- Extends the rani_memories CHECK constraints so visual memory notes
-- (text descriptions of things she saw) can sync:
--   kind:   'visual' added
--   source: 'awareness' AND 'screen' added — the old source list in
--           migration 016 is ('live_call','chat') only (verified from
--           the 016 SQL); awareness/camera notes and screen-watch notes
--           need their own sources.
-- IDEMPOTENT: safe to run more than once (old constraints dropped by
-- name only if they exist; new ones created only if absent).
-- DO NOT RUN from the app — run once from the Supabase SQL editor,
-- AFTER the v3.0.0 APK is out. Until it is run, the app keeps visual
-- rows on-device and retries later (never a crash).

do $$
begin
  -- kind CHECK: ('conversation','summary','fact') -> + 'visual'
  if exists (
    select 1 from pg_constraint
    where conname = 'rani_memories_kind_check'
      and conrelid = 'public.rani_memories'::regclass
  ) then
    alter table public.rani_memories drop constraint rani_memories_kind_check;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'rani_memories_kind_check'
      and conrelid = 'public.rani_memories'::regclass
  ) then
    alter table public.rani_memories
      add constraint rani_memories_kind_check
      check (kind in ('conversation', 'summary', 'fact', 'visual'));
  end if;

  -- source CHECK: ('live_call','chat') -> + 'awareness', 'screen'
  if exists (
    select 1 from pg_constraint
    where conname = 'rani_memories_source_check'
      and conrelid = 'public.rani_memories'::regclass
  ) then
    alter table public.rani_memories drop constraint rani_memories_source_check;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'rani_memories_source_check'
      and conrelid = 'public.rani_memories'::regclass
  ) then
    alter table public.rani_memories
      add constraint rani_memories_source_check
      check (source in ('live_call', 'chat', 'awareness', 'screen'));
  end if;
end $$;
