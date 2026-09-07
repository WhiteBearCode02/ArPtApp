-- Run in the Supabase SQL editor after creating the report tables.
alter table public.exercise_sessions
    add column if not exists user_id uuid references auth.users(id);

alter table public.exercise_sessions enable row level security;
alter table public.exercise_rep_records enable row level security;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select coalesce((auth.jwt() -> 'app_metadata' ->> 'role') = 'admin', false)
        or coalesce((auth.jwt() ->> 'email') = current_setting('app.admin_email', true), false);
$$;

drop policy if exists "users insert own sessions" on public.exercise_sessions;
create policy "users insert own sessions"
on public.exercise_sessions
for insert to authenticated
with check (user_id = auth.uid());

drop policy if exists "users read own sessions" on public.exercise_sessions;
create policy "users read own sessions"
on public.exercise_sessions
for select to authenticated
using (user_id = auth.uid() or public.is_admin());

drop policy if exists "users insert own rep records" on public.exercise_rep_records;
create policy "users insert own rep records"
on public.exercise_rep_records
for insert to authenticated
with check (
    exists (
        select 1 from public.exercise_sessions s
        where s.id = session_id and (s.user_id = auth.uid() or public.is_admin())
    )
);

drop policy if exists "users read own rep records" on public.exercise_rep_records;
create policy "users read own rep records"
on public.exercise_rep_records
for select to authenticated
using (
    exists (
        select 1 from public.exercise_sessions s
        where s.id = session_id and (s.user_id = auth.uid() or public.is_admin())
    )
);
