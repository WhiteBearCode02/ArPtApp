-- REVIEWED DEPLOYMENT SCRIPT, NOT APPLIED AUTOMATICALLY.
-- Existing schema inspected read-only 2026-11-01. CLI unavailable here; no fabricated migration ID.
-- After review, use `supabase migration new workout_history_sync` and copy this SQL into its output.
-- No secrets, no DELETE/UPDATE permission, no SECURITY DEFINER / RLS bypass.
begin;

alter table public.exercise_sessions
  add column if not exists workout_date text,
  add column if not exists exact_average_score real,
  add column if not exists report_json text;

create unique index if not exists exercise_rep_records_session_rep_uidx
  on public.exercise_rep_records(session_id, rep_number);
create index if not exists exercise_sessions_user_created_id_idx
  on public.exercise_sessions(user_id, created_at desc, id desc);

-- Refuse to silently leave an unknown permissive policy in place. Review it explicitly first.
do $$ begin
  if exists (select 1 from pg_policies where schemaname = 'public' and
    ((tablename = 'exercise_sessions' and policyname not in ('users insert own sessions', 'users read own sessions')) or
     (tablename = 'exercise_rep_records' and policyname not in ('users insert own rep records', 'users read own rep records'))))
  then raise exception 'Unexpected workout RLS policy: manual review required'; end if;
end $$;

alter table public.exercise_sessions enable row level security;
alter table public.exercise_rep_records enable row level security;
revoke all on public.exercise_sessions, public.exercise_rep_records from public, anon, authenticated;
grant select, insert on public.exercise_sessions, public.exercise_rep_records to authenticated;
grant usage, select on sequence public.exercise_rep_records_id_seq to authenticated;

drop policy if exists "users insert own sessions" on public.exercise_sessions;
create policy "users insert own sessions" on public.exercise_sessions for insert to authenticated
  with check (user_id = (select auth.uid()));
drop policy if exists "users read own sessions" on public.exercise_sessions;
create policy "users read own sessions" on public.exercise_sessions for select to authenticated
  using (user_id = (select auth.uid()) or
    coalesce((select auth.jwt())->'app_metadata'->>'role' = 'admin', false));
drop policy if exists "users insert own rep records" on public.exercise_rep_records;
create policy "users insert own rep records" on public.exercise_rep_records for insert to authenticated
  with check (exists(select 1 from public.exercise_sessions s
    where s.id = session_id and s.user_id = (select auth.uid())));
drop policy if exists "users read own rep records" on public.exercise_rep_records;
create policy "users read own rep records" on public.exercise_rep_records for select to authenticated
  using (exists(select 1 from public.exercise_sessions s where s.id = session_id and
    (s.user_id = (select auth.uid()) or coalesce((select auth.jwt())->'app_metadata'->>'role' = 'admin', false))));
-- Existing trusted app_metadata admin reporting remains intact. The personal-history adapter
-- always applies user_id ownership too; ordinary users cannot edit app_metadata.

create or replace function public.save_workout_snapshot(p_session jsonb, p_reps jsonb)
returns void language plpgsql security invoker set search_path = '' as $$
declare
  v public.exercise_sessions%rowtype;
  existing public.exercise_sessions%rowtype;
  r public.exercise_rep_records%rowtype;
  item jsonb;
begin
  if auth.uid() is null or jsonb_typeof(p_session) is distinct from 'object'
     or jsonb_typeof(p_reps) is distinct from 'array' then
    raise exception 'Invalid workout request' using errcode = '22023';
  end if;
  if octet_length(p_session::text) > 2097152 or octet_length(p_reps::text) > 2097152
     or jsonb_array_length(p_reps) > 1000 then raise exception 'Workout payload too large'; end if;
  if exists (select 1 from jsonb_object_keys(p_session) k where k not in
    ('id','user_id','exercise_type','total_reps','average_score','exact_average_score','feedback_message',
     'duration_seconds','burned_calories','created_at','workout_date','report_json')) then
    raise exception 'Unexpected workout field';
  end if;
  v := jsonb_populate_record(null::public.exercise_sessions, p_session);
  if v.user_id is distinct from auth.uid() or v.id is null or
     v.id !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
    raise exception 'Workout owner or ID invalid' using errcode = '42501';
  end if;
  if v.total_reps is null or v.total_reps not between 0 and 1000
     or v.average_score is null or v.average_score not between 0 and 100
     or v.exact_average_score is null or v.exact_average_score not between 0 and 100
     or v.duration_seconds is null or v.duration_seconds not between 0 and 604800
     or v.burned_calories is null or v.burned_calories not between 0 and 100000
     or v.created_at is null or v.workout_date is null
     or v.workout_date !~ '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$'
     or v.report_json is null or length(v.report_json) > 512000
     or v.exercise_type is null or length(v.exercise_type) > 100
     or v.feedback_message is null or length(v.feedback_message) > 16000 then
    raise exception 'Invalid workout values' using errcode = '22023';
  end if;
  -- Parsing ensures text is valid JSON, not an arbitrary file or binary payload.
  perform v.report_json::jsonb;
  insert into public.exercise_sessions(id,user_id,exercise_type,total_reps,average_score,exact_average_score,
      feedback_message,duration_seconds,burned_calories,created_at,workout_date,report_json)
    values(v.id,v.user_id,v.exercise_type,v.total_reps,v.average_score,v.exact_average_score,
      v.feedback_message,v.duration_seconds,v.burned_calories,v.created_at,v.workout_date,v.report_json)
    on conflict (id) do nothing;
  select * into existing from public.exercise_sessions where id = v.id and user_id = auth.uid();
  if not found then raise exception 'Workout owner mismatch' using errcode = '42501'; end if;
  -- Ignore-duplicate must not acknowledge a different snapshot under the same UUID.
  if row(existing.exercise_type,existing.total_reps,existing.average_score,existing.exact_average_score,
         existing.feedback_message,existing.duration_seconds,existing.burned_calories,existing.created_at,
         existing.workout_date,existing.report_json) is distinct from
     row(v.exercise_type,v.total_reps,v.average_score,v.exact_average_score,v.feedback_message,
         v.duration_seconds,v.burned_calories,v.created_at,v.workout_date,v.report_json) then
    raise exception 'Workout UUID conflict';
  end if;
  if (select count(distinct x->>'rep_number') from jsonb_array_elements(p_reps) x) <> jsonb_array_length(p_reps)
  then raise exception 'Duplicate rep numbers'; end if;
  for item in select value from jsonb_array_elements(p_reps) loop
    if jsonb_typeof(item) is distinct from 'object' or exists
      (select 1 from jsonb_object_keys(item) k where k not in ('session_id','rep_number','max_angle','sway_x',
       'score','detail','error_tags','eccentric_duration_ms','concentric_duration_ms','pose_score'))
    then raise exception 'Unexpected rep field'; end if;
    r := jsonb_populate_record(null::public.exercise_rep_records, item);
    if r.session_id is distinct from v.id or r.rep_number is null or r.rep_number not between 1 and 1000
       or r.max_angle is null or r.max_angle not between -10000 and 10000
       or r.sway_x is null or r.sway_x not between -10000 and 10000
       or (r.score is not null and r.score not between 0 and 100)
       or (r.pose_score is not null and r.pose_score not between 0 and 100)
       or r.eccentric_duration_ms is null or r.eccentric_duration_ms < 0
       or r.concentric_duration_ms is null or r.concentric_duration_ms < 0
       or r.detail is null or length(r.detail) > 16000
       or jsonb_typeof(r.error_tags) is distinct from 'array' then raise exception 'Invalid rep values'; end if;
    insert into public.exercise_rep_records(session_id,rep_number,max_angle,sway_x,score,detail,error_tags,
        eccentric_duration_ms,concentric_duration_ms,pose_score)
      values(r.session_id,r.rep_number,r.max_angle,r.sway_x,r.score,r.detail,r.error_tags,
        r.eccentric_duration_ms,r.concentric_duration_ms,r.pose_score)
      on conflict(session_id,rep_number) do nothing;
    if not exists (select 1 from public.exercise_rep_records e where e.session_id = v.id
       and e.rep_number = r.rep_number and
       row(e.max_angle,e.sway_x,e.score,e.detail,e.error_tags,e.eccentric_duration_ms,e.concentric_duration_ms,e.pose_score)
       is not distinct from row(r.max_angle,r.sway_x,r.score,r.detail,r.error_tags,r.eccentric_duration_ms,r.concentric_duration_ms,r.pose_score))
    then raise exception 'Workout rep conflict'; end if;
  end loop;
end;
$$;
revoke all on function public.save_workout_snapshot(jsonb,jsonb) from public, anon, authenticated;
grant execute on function public.save_workout_snapshot(jsonb,jsonb) to authenticated;
commit;
