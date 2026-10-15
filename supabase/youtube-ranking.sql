-- REVIEWED SOURCE ONLY: do not apply/deploy without separate approval.
-- Per-user request quota below is required BEFORE deploying the hardened function (missing RPC => 503).
-- The Edge Function alone can read these tables through its service role key.
begin;
create table if not exists public.youtube_api_usage (
    id bigint generated always as identity primary key,
    usage_date date not null unique,
    search_count integer not null default 0 check (search_count >= 0),
    updated_at timestamptz not null default now()
);

create table if not exists public.youtube_ranking_cache (
    id bigint generated always as identity primary key,
    category text not null unique check (category in ('SQUAT', 'SHOULDER_PRESS')),
    query text not null,
    payload jsonb not null,
    fetched_at timestamptz not null,
    expires_at timestamptz not null check (expires_at > fetched_at)
);

create index if not exists youtube_ranking_cache_expires_at_idx
    on public.youtube_ranking_cache (expires_at);

alter table public.youtube_api_usage enable row level security;
alter table public.youtube_ranking_cache enable row level security;
revoke all on table public.youtube_api_usage, public.youtube_ranking_cache from public, anon, authenticated;
grant select, insert, update on table public.youtube_api_usage, public.youtube_ranking_cache to service_role;
grant usage, select on sequence public.youtube_api_usage_id_seq, public.youtube_ranking_cache_id_seq to service_role;

-- INSERT ... ON CONFLICT takes a row lock, so concurrent requests cannot exceed the limit.
-- The limit is passed by the Edge Function from its single config module.
create or replace function public.reserve_youtube_search_call(p_daily_limit integer)
returns integer
language plpgsql
security invoker
set search_path = ''
as $$
declare
    current_count integer;
    today_kst date := (now() at time zone 'Asia/Seoul')::date;
begin
    if p_daily_limit < 1 or p_daily_limit > 100 then
        raise exception 'invalid daily limit';
    end if;

    insert into public.youtube_api_usage (usage_date, search_count)
    values (today_kst, 1)
    on conflict (usage_date) do update
        set search_count = public.youtube_api_usage.search_count + 1,
            updated_at = now()
        where public.youtube_api_usage.search_count < p_daily_limit
    returning search_count into current_count;

    return case when current_count is null then -1 else p_daily_limit - current_count end;
end;
$$;

revoke all on function public.reserve_youtube_search_call(integer) from public, anon, authenticated;
grant execute on function public.reserve_youtube_search_call(integer) to service_role;

-- All valid requests, INCLUDING cache hits, use a separate authenticated quota.
-- Exactly one row per account: no IP, email, category, profile or search history stored here.
create schema if not exists youtube_private;
revoke all on schema youtube_private from public, anon, authenticated;
create table if not exists youtube_private.request_limits (
    user_id uuid primary key references auth.users(id) on delete cascade,
    request_day date not null,
    request_minute timestamptz not null,
    day_count integer not null check (day_count between 1 and 120),
    minute_count integer not null check (minute_count between 1 and 6)
);
alter table youtube_private.request_limits enable row level security;
revoke all on youtube_private.request_limits from public, anon, authenticated;

-- Private narrow definer: caller cannot supply UUID, clock, limits or reset the counter.
create or replace function youtube_private.reserve_request()
returns boolean language plpgsql security definer set search_path = '' as $$
declare
    owner_id uuid := auth.uid();
    today date := (pg_catalog.now() at time zone 'UTC')::date;
    this_minute timestamptz := pg_catalog.date_trunc('minute', pg_catalog.now());
    reserved uuid;
begin
    if owner_id is null or coalesce((auth.jwt()->>'is_anonymous')::boolean, true) then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    -- Conditional atomic upsert locks the account row across concurrent Edge isolates.
    insert into youtube_private.request_limits as quota
        (user_id, request_day, request_minute, day_count, minute_count)
    values (owner_id, today, this_minute, 1, 1)
    on conflict (user_id) do update set
        request_day = today,
        request_minute = this_minute,
        day_count = case when quota.request_day = today then quota.day_count + 1 else 1 end,
        minute_count = case when quota.request_minute = this_minute then quota.minute_count + 1 else 1 end
    where (quota.request_day <> today or quota.day_count < 120)
      and (quota.request_minute <> this_minute or quota.minute_count < 6)
    returning user_id into reserved;
    return reserved is not null;
end;
$$;
revoke all on function youtube_private.reserve_request() from public, anon, authenticated;
grant usage on schema youtube_private to authenticated;
grant execute on function youtube_private.reserve_request() to authenticated;

create or replace function public.reserve_youtube_ranking_request()
returns boolean language sql security invoker set search_path = '' as $$
    select youtube_private.reserve_request();
$$;
revoke all on function public.reserve_youtube_ranking_request() from public, anon, authenticated;
grant execute on function public.reserve_youtube_ranking_request() to authenticated;
commit;
