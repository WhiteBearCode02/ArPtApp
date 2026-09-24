-- Exercise report schema and owner-only Data API access.
-- This file is idempotent for the current AIRPTCoach schema.

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'exercise_sessions' and column_name = 'session_id'
    ) and not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'exercise_sessions' and column_name = 'id'
    ) then
        alter table public.exercise_sessions rename column session_id to id;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'exercise_rep_records' and column_name = 'swayx'
    ) and not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'exercise_rep_records' and column_name = 'sway_x'
    ) then
        alter table public.exercise_rep_records rename column swayx to sway_x;
    end if;
end
$$;

alter table public.exercise_sessions
    add column if not exists user_id uuid references auth.users(id) on delete cascade,
    add column if not exists duration_seconds bigint not null default 0 check (duration_seconds >= 0),
    add column if not exists burned_calories double precision not null default 0 check (burned_calories >= 0);

alter table public.exercise_sessions alter column user_id set not null;

alter table public.exercise_rep_records
    add column if not exists score real check (score is null or score between 0 and 100),
    add column if not exists detail text not null default '',
    add column if not exists error_tags jsonb not null default '[]'::jsonb
        check (jsonb_typeof(error_tags) = 'array'),
    add column if not exists eccentric_duration_ms bigint not null default 0
        check (eccentric_duration_ms >= 0),
    add column if not exists concentric_duration_ms bigint not null default 0
        check (concentric_duration_ms >= 0),
    add column if not exists pose_score real check (pose_score is null or pose_score between 0 and 100);

create unique index if not exists exercise_rep_records_session_rep_uidx
    on public.exercise_rep_records (session_id, rep_number);
create index if not exists exercise_sessions_user_created_idx
    on public.exercise_sessions (user_id, created_at desc);
create index if not exists exercise_rep_records_session_idx
    on public.exercise_rep_records (session_id);

alter table public.exercise_sessions enable row level security;
alter table public.exercise_rep_records enable row level security;
revoke all on table public.exercise_sessions, public.exercise_rep_records from anon, authenticated;
grant select, insert on table public.exercise_sessions, public.exercise_rep_records to authenticated;
grant usage, select on sequence public.exercise_rep_records_id_seq to authenticated;

drop policy if exists "anonymous session insert" on public.exercise_sessions;
drop policy if exists "anonymous rep insert" on public.exercise_rep_records;
drop policy if exists "users insert own sessions" on public.exercise_sessions;
drop policy if exists "users read own sessions" on public.exercise_sessions;
drop policy if exists "users insert own rep records" on public.exercise_rep_records;
drop policy if exists "users read own rep records" on public.exercise_rep_records;

create policy "users insert own sessions" on public.exercise_sessions
for insert to authenticated with check ((select auth.uid()) = user_id);

create policy "users read own sessions" on public.exercise_sessions
for select to authenticated using (
    (select auth.uid()) = user_id
    or coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin', false)
);

create policy "users insert own rep records" on public.exercise_rep_records
for insert to authenticated with check (
    exists (
        select 1 from public.exercise_sessions s
        where s.id = session_id and s.user_id = (select auth.uid())
    )
);

create policy "users read own rep records" on public.exercise_rep_records
for select to authenticated using (
    exists (
        select 1 from public.exercise_sessions s
        where s.id = session_id and (
            s.user_id = (select auth.uid())
            or coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin', false)
        )
    )
);

create table if not exists public.exercise_rep_joint_metrics (
    id bigint generated always as identity primary key,
    rep_record_id integer not null references public.exercise_rep_records(id) on delete cascade,
    joint_name text not null check (char_length(joint_name) between 1 and 64),
    min_angle real,
    max_angle real,
    target_angle real,
    accuracy_score real check (accuracy_score is null or accuracy_score between 0 and 100),
    error_code text,
    feedback text not null default '',
    created_at timestamptz not null default timezone('utc', now()),
    unique (rep_record_id, joint_name)
);

create index if not exists exercise_rep_joint_metrics_rep_idx
    on public.exercise_rep_joint_metrics (rep_record_id);
alter table public.exercise_rep_joint_metrics enable row level security;
revoke all on table public.exercise_rep_joint_metrics from anon, authenticated;
grant select, insert on table public.exercise_rep_joint_metrics to authenticated;
grant usage, select on sequence public.exercise_rep_joint_metrics_id_seq to authenticated;

drop policy if exists "users insert own joint metrics" on public.exercise_rep_joint_metrics;
drop policy if exists "users read own joint metrics" on public.exercise_rep_joint_metrics;

create policy "users insert own joint metrics" on public.exercise_rep_joint_metrics
for insert to authenticated with check (
    exists (
        select 1 from public.exercise_rep_records r
        join public.exercise_sessions s on s.id = r.session_id
        where r.id = rep_record_id and s.user_id = (select auth.uid())
    )
);

create policy "users read own joint metrics" on public.exercise_rep_joint_metrics
for select to authenticated using (
    exists (
        select 1 from public.exercise_rep_records r
        join public.exercise_sessions s on s.id = r.session_id
        where r.id = rep_record_id and (
            s.user_id = (select auth.uid())
            or coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin', false)
        )
    )
);
