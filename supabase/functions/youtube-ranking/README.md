# YouTube ranking

This Edge Function returns public YouTube videos for `SQUAT` and
`SHOULDER_PRESS`, sorted by `viewCount` descending among the returned search
candidates. The Android app never receives the YouTube API key.

## Setup

1. In the Supabase SQL Editor, apply `supabase/youtube-ranking.sql`. It creates
   `youtube_api_usage`, `youtube_ranking_cache`, and the atomic reservation RPC.
   The tables have RLS enabled and no `anon` or `authenticated` grants.
2. In Google Cloud, enable YouTube Data API v3 and create a key restricted to
   that API. Keep its application restriction compatible with calls from
   Supabase Edge Functions.
3. In Supabase Edge Function Secrets, set `YOUTUBE_API_KEY` to that key. Never
   put it in Android resources, `local.properties`, or Git.
4. Deploy `youtube-ranking` with the Supabase CLI or Dashboard. The Android
   client sends `POST /functions/v1/youtube-ranking` with a body such as
   `{"category":"SQUAT"}`.

The only configurable limits and queries are in [`config.ts`](config.ts):
`MAX_DAILY_SEARCH_CALLS = 30` and `CACHE_TTL_HOURS = 3`. A valid cache hit
does not call YouTube or consume the search counter. After expiry, one
`search.list` call is atomically reserved before searching and one batched
`videos.list` call retrieves details. At the daily limit, the latest cached
result is returned with `cacheStatus: "STALE"` and
`message: "할당 조회수 모두 사용"`. If no cached result exists, the function returns
HTTP 429. The counter resets at midnight in Asia/Seoul.

The cap covers this function only. Other consumers of the same Google Cloud
project can also use its daily quota. Supabase Free plan and Google Cloud quota
settings should be confirmed before deployment.
