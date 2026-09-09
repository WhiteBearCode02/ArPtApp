# YouTube exercise trends Edge Function

This function aggregates only public YouTube video data for fixed exercise queries. It does not access personal search history, scrape YouTube, download video media, or expose `YOUTUBE_API_KEY` to the Android app.

## Deployment

```bash
supabase secrets set YOUTUBE_API_KEY=YOUR_RESTRICTED_YOUTUBE_DATA_API_KEY
supabase functions deploy youtube-trends
```

Create the key in Google Cloud Console, enable **YouTube Data API v3**, and restrict the key to that API. Keep it only as the Edge Function secret.

The response is a relative public-video interest score based on the view counts of five `KR`/Korean search results for each fixed query. It is not a measurement of personal or global YouTube search volume.
