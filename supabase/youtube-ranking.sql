-- Apply in Supabase SQL Editor before deploying youtube-ranking.
-- The Edge Function alone can read these tables through its service role key.
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
