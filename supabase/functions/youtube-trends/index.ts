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
  "Cache-Control": "no-store",
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers })

class QuotaExceededError extends Error {}

async function youtubeResponse(response: Response) {
  if (response.ok) return response.json()
  const body = await response.text()
  if ((response.status === 403 || response.status === 429) &&
    /quotaExceeded|dailyLimitExceeded|rateLimitExceeded|quota/i.test(body)) {
    throw new QuotaExceededError("YouTube quota exhausted")
  }
  throw new Error(`YouTube request failed: ${response.status}`)
}

async function reserveFreeRequest(): Promise<number> {
  const url = Deno.env.get("SUPABASE_URL")
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")
  if (!url || !serviceKey) throw new Error("Quota service is not configured")

  const response = await fetch(`${url}/rest/v1/rpc/reserve_youtube_trend_request`, {
    method: "POST",
    headers: {
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
      "Content-Type": "application/json",
    },
    body: "{}",
  })
  if (!response.ok) throw new Error(`Quota reservation failed: ${response.status}`)
  const remaining = await response.json()
  if (typeof remaining !== "number") throw new Error("Invalid quota response")
  return remaining
}

async function viewTotal(key: string, query: string) {
  const searchUrl = new URL(SEARCH)
  searchUrl.search = new URLSearchParams({
    key, part: "snippet", type: "video", order: "viewCount", maxResults: "5",
    regionCode: "KR", relevanceLanguage: "ko", safeSearch: "moderate", q: query,
  }).toString()
  const searchResponse = await fetch(searchUrl)
  const searchData = await youtubeResponse(searchResponse)
  const ids = (searchData.items ?? []).flatMap((item: { id?: { videoId?: string } }) =>
    item.id?.videoId ? [item.id.videoId] : [])
  if (ids.length === 0) return { total: 0, sampleSize: 0 }

  const videoUrl = new URL(VIDEOS)
  videoUrl.search = new URLSearchParams({ key, part: "statistics", id: ids.join(",") }).toString()
  const videoResponse = await fetch(videoUrl)
  const videoData = await youtubeResponse(videoResponse)
  const total = (videoData.items ?? []).reduce(
    (sum: number, item: { statistics?: { viewCount?: string } }) => sum + Number(item.statistics?.viewCount ?? 0), 0,
  )
  return { total, sampleSize: ids.length }
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers })
  if (request.method !== "POST") return json({ message: "Method not allowed" }, 405)

  const key = Deno.env.get("YOUTUBE_API_KEY")
  if (!key) return json({ message: "YouTube trend service is not configured." }, 503)

  let remainingRequests: number
  try {
    remainingRequests = await reserveFreeRequest()
  } catch (error) {
    console.error(error)
    return json({ message: "무료 조회 한도를 확인할 수 없습니다." }, 503)
  }
  if (remainingRequests < 0) return json({ message: "할당 조회수 모두 사용" }, 429)

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
    return json({ updatedAt: new Date().toISOString(), remainingRequests, trends })
  } catch (error) {
    console.error(error)
    if (error instanceof QuotaExceededError) return json({ message: "할당 조회수 모두 사용" }, 429)
    return json({ message: "YouTube trend data is temporarily unavailable." }, 502)
  }
})
