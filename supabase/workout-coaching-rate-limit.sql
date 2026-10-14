-- OPTIONAL REVIEW TEMPLATE, NOT AN APPLIED MIGRATION. See docs/LLM_INTEGRATION.md.
-- No workout/profile/video payload here: only account UUID + UTC day + count.
begin;
create schema if not exists coaching_private;
revoke all on schema coaching_private from public, anon;
create table if not exists coaching_private.daily_requests (
    user_id uuid not null references auth.users(id) on delete cascade,
    request_day date not null,
    requests integer not null check (requests between 1 and 5),
    primary key (user_id, request_day)
);
alter table coaching_private.daily_requests enable row level security;
revoke all on coaching_private.daily_requests from public, anon, authenticated;

-- Narrow definer function lives in a non-exposed schema. No dynamic SQL / caller userId.
create or replace function coaching_private.reserve_request(p_limit integer default 5)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
    owner_id uuid := auth.uid();
    used integer;
    today date := (pg_catalog.now() at time zone 'UTC')::date;
begin
    if owner_id is null or coalesce((auth.jwt()->>'is_anonymous')::boolean, false) then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    if p_limit is null or p_limit not between 1 and 5 then
        raise exception 'Invalid daily limit' using errcode = '22023';
    end if;
    -- Unique row + conditional atomic upsert enforces the cap across concurrent isolates.
    insert into coaching_private.daily_requests as quota (user_id, request_day, requests)
    values (owner_id, today, 1)
    on conflict (user_id, request_day) do update set requests = quota.requests + 1
      where quota.requests < p_limit
    returning requests into used;
    return used is not null;
end;
$$;
revoke all on function coaching_private.reserve_request(integer) from public, anon;
grant usage on schema coaching_private to authenticated;
grant execute on function coaching_private.reserve_request(integer) to authenticated;

create or replace function public.reserve_workout_coaching_request(p_limit integer default 5)
returns boolean language sql security invoker set search_path = '' as $$
    select coaching_private.reserve_request(p_limit);
$$;
revoke all on function public.reserve_workout_coaching_request(integer) from public, anon;
grant execute on function public.reserve_workout_coaching_request(integer) to authenticated;
commit;

-- Retention: an operator may manually delete rows older than 30 days in coaching_private.
-- Do not expose that schema or grant table access. No Cron/service-role/admin key added.
