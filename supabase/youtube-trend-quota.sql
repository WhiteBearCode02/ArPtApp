-- Run once in the Supabase SQL Editor before deploying youtube-trends.
-- One successful reservation permits one function request (three YouTube searches).
-- 30 requests/day = at most 90 search.list calls/day, below the default 100.
create table if not exists public.youtube_trend_daily_quota (
    id boolean primary key default true check (id),
    quota_day date not null,
    used_requests integer not null check (used_requests between 0 and 30)
);

alter table public.youtube_trend_daily_quota enable row level security;
revoke all on table public.youtube_trend_daily_quota from public, anon, authenticated;
grant select, insert, update on table public.youtube_trend_daily_quota to service_role;

create or replace function public.reserve_youtube_trend_request()
returns integer
language plpgsql
security invoker
set search_path = ''
as $$
declare
    new_used integer;
    today_kst date := (now() at time zone 'Asia/Seoul')::date;
begin
    insert into public.youtube_trend_daily_quota (id, quota_day, used_requests)
    values (true, today_kst, 1)
    on conflict (id) do update
        set quota_day = today_kst,
            used_requests = case
                when public.youtube_trend_daily_quota.quota_day = today_kst
                    then public.youtube_trend_daily_quota.used_requests + 1
                else 1
            end
        where public.youtube_trend_daily_quota.quota_day <> today_kst
           or public.youtube_trend_daily_quota.used_requests < 30
    returning used_requests into new_used;

    return case when new_used is null then -1 else 30 - new_used end;
end;
$$;

revoke all on function public.reserve_youtube_trend_request() from public, anon, authenticated;
grant execute on function public.reserve_youtube_trend_request() to service_role;
