-- v7.3 (migration 011): Dost Room — group chat messages
create table if not exists public.dost_messages (
  id bigint generated always as identity primary key,
  room text not null default 'aam',
  uid text,
  name text,
  text text not null,
  created_at timestamptz not null default now()
);
alter table public.dost_messages enable row level security;
-- koi public policy nahi: sirf worker (service role) parhe/likhe ga, login lazmi hai
