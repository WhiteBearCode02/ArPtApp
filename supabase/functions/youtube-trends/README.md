# YouTube exercise trends Edge Function

This function aggregates only public YouTube video data for fixed exercise queries. It does not access personal search history, scrape YouTube, download video media, or expose `YOUTUBE_API_KEY` to the Android app.

## Deployment

Before deployment, run [`supabase/youtube-trend-quota.sql`](../../youtube-trend-quota.sql)
in the Supabase SQL Editor. The function fails closed if the quota table or RPC is
missing. The database atomically reserves at most 30 requests per Korea calendar
day. Each request performs three `search.list` calls, so this feature uses at
most 90 of the default 100 daily search calls. Reservations are kept even when
a YouTube request fails, because an attempted request may still use quota.

```bash
supabase secrets set YOUTUBE_API_KEY=YOUR_RESTRICTED_YOUTUBE_DATA_API_KEY
supabase functions deploy youtube-trends
```

Create the key in Google Cloud Console, enable **YouTube Data API v3**, and restrict the key to that API. Keep it only as the Edge Function secret.

The Android home screen sends a POST request to this function only when the user taps **조회**.
When the daily budget is exhausted, the function returns HTTP 429 with
`{"message":"할당 조회수 모두 사용"}` and does not call YouTube. Successful
responses include `remainingRequests`. The daily counter resets at midnight
in Asia/Seoul.

The response is a relative public-video interest score based on the view counts of five `KR`/Korean search results for each fixed query. It is not a measurement of personal or global YouTube search volume. This cap applies only to this function; other clients sharing the Google Cloud project also consume its quota.
