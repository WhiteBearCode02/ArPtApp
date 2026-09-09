// Uses official public-video data only: no user history, scraping, or media downloads.
const SEARCH = "https://www.googleapis.com/youtube/v3/search"
const VIDEOS = "https://www.googleapis.com/youtube/v3/videos"

const QUERIES = [
  { exercise: "Squat", query: "squat exercise" },
  { exercise: "Full body workout", query: "full body workout" },
  { exercise: "Shoulder press", query: "shoulder press exercise" },
]

const headers = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "public, max-age=21600",
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers })

async function viewTotal(key: string, query: string) {
  const searchUrl = new URL(SEARCH)
  searchUrl.search = new URLSearchParams({
    key, part: "snippet", type: "video", order: "viewCount", maxResults: "5",
    regionCode: "KR", relevanceLanguage: "ko", safeSearch: "moderate", q: query,
  }).toString()
  const searchResponse = await fetch(searchUrl)
  if (!searchResponse.ok) throw new Error(`Search failed: ${searchResponse.status}`)
  const searchData = await searchResponse.json()
  const ids = (searchData.items ?? []).flatMap((item: { id?: { videoId?: string } }) =>
    item.id?.videoId ? [item.id.videoId] : [])
  if (ids.length === 0) return { total: 0, sampleSize: 0 }

  const videoUrl = new URL(VIDEOS)
  videoUrl.search = new URLSearchParams({ key, part: "statistics", id: ids.join(",") }).toString()
  const videoResponse = await fetch(videoUrl)
  if (!videoResponse.ok) throw new Error(`Statistics failed: ${videoResponse.status}`)
  const videoData = await videoResponse.json()
  const total = (videoData.items ?? []).reduce(
    (sum: number, item: { statistics?: { viewCount?: string } }) => sum + Number(item.statistics?.viewCount ?? 0), 0,
  )
  return { total, sampleSize: ids.length }
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers })
  if (request.method !== "GET") return json({ message: "Method not allowed" }, 405)

  const key = Deno.env.get("YOUTUBE_API_KEY")
  if (!key) return json({ message: "YouTube trend service is not configured." }, 503)

  try {
    const raw = await Promise.all(QUERIES.map(async ({ exercise, query }) => ({
      exercise, ...(await viewTotal(key, query)),
    })))
    const maximum = Math.max(...raw.map((trend) => trend.total), 1)
    const trends = raw.map((trend) => ({
      exercise: trend.exercise,
      // A relative public-video interest score, never a search-volume claim.
      score: Math.round((trend.total / maximum) * 100),
      sampleSize: trend.sampleSize,
    })).sort((left, right) => right.score - left.score)
    return json({ updatedAt: new Date().toISOString(), trends })
  } catch (error) {
    console.error(error)
    return json({ message: "YouTube trend data is temporarily unavailable." }, 502)
  }
})
