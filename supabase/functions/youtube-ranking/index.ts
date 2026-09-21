import {
  CACHE_TTL_HOURS,
  MAX_DAILY_SEARCH_CALLS,
  RANKING_QUERIES,
  type RankingCategory,
} from "./config.ts"

const SEARCH_URL = "https://www.googleapis.com/youtube/v3/search"
const VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos"
const QUOTA_MESSAGE = "할당 조회수 모두 사용"
const headers = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
}

type RankedVideo = {
  videoId: string
  title: string
  channelTitle: string
  thumbnailUrl: string | null
  publishedAt: string | null
  viewCount: string | null
  likeCount: string | null
  commentCount: string | null
  videoUrl: string
}

type RankingPayload = {
  category: RankingCategory
  query: string
  rankingPolicy: "VIEW_COUNT_DESC"
  videos: RankedVideo[]
}

type CacheRow = {
  query: string
  payload: RankingPayload
  fetched_at: string
  expires_at: string
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers })

class QuotaExceededError extends Error {}

function serviceConfig() {
  const url = Deno.env.get("SUPABASE_URL")
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")
  if (!url || !key) throw new Error("Supabase service is unavailable")
  return { url, key }
}

function serviceHeaders(key: string) {
  return {
    apikey: key,
    Authorization: `Bearer ${key}`,
    "Content-Type": "application/json",
  }
}

async function readCache(category: RankingCategory): Promise<CacheRow | null> {
  const { url, key } = serviceConfig()
  const endpoint = new URL(`${url}/rest/v1/youtube_ranking_cache`)
  endpoint.search = new URLSearchParams({
    select: "query,payload,fetched_at,expires_at",
    category: `eq.${category}`,
    limit: "1",
  }).toString()
  const response = await fetch(endpoint, { headers: serviceHeaders(key) })
  if (!response.ok) throw new Error("Cache read failed")
  const rows = await response.json() as CacheRow[]
  return rows[0] ?? null
}

async function reserveSearchCall(): Promise<number> {
  const { url, key } = serviceConfig()
  const response = await fetch(`${url}/rest/v1/rpc/reserve_youtube_search_call`, {
    method: "POST",
    headers: serviceHeaders(key),
    body: JSON.stringify({ p_daily_limit: MAX_DAILY_SEARCH_CALLS }),
  })
  if (!response.ok) throw new Error("Quota reservation failed")
  const remaining = await response.json()
  if (typeof remaining !== "number") throw new Error("Invalid quota response")
  return remaining
}

async function writeCache(payload: RankingPayload, fetchedAt: string, expiresAt: string) {
  const { url, key } = serviceConfig()
  const response = await fetch(`${url}/rest/v1/youtube_ranking_cache?on_conflict=category`, {
    method: "POST",
    headers: {
      ...serviceHeaders(key),
      Prefer: "resolution=merge-duplicates,return=minimal",
    },
    body: JSON.stringify({
      category: payload.category,
      query: payload.query,
      payload,
      fetched_at: fetchedAt,
      expires_at: expiresAt,
    }),
  })
  if (!response.ok) throw new Error("Cache write failed")
}

async function youtubeJson(url: URL, key: string): Promise<Record<string, unknown>> {
  const response = await fetch(url, { headers: { "x-goog-api-key": key } })
  if (!response.ok) {
    const body = await response.text()
    if ((response.status === 403 || response.status === 429) &&
      /quotaExceeded|dailyLimitExceeded|rateLimitExceeded|quota/i.test(body)) {
      throw new QuotaExceededError()
    }
    throw new Error("YouTube request failed")
  }
  return await response.json()
}

async function fetchRanking(category: RankingCategory, query: string, key: string): Promise<RankingPayload> {
  const searchUrl = new URL(SEARCH_URL)
  searchUrl.search = new URLSearchParams({
    part: "snippet", type: "video", order: "viewCount", maxResults: "10",
    regionCode: "KR", relevanceLanguage: "ko", safeSearch: "moderate", q: query,
  }).toString()
  const search = await youtubeJson(searchUrl, key)
  const results = Array.isArray(search.items) ? search.items : []
  const ids = results.flatMap((item: { id?: { videoId?: string } }) =>
    item.id?.videoId ? [item.id.videoId] : []) as string[]

  let videos: RankedVideo[] = []
  if (ids.length > 0) {
    const videoUrl = new URL(VIDEOS_URL)
    videoUrl.search = new URLSearchParams({ part: "snippet,statistics", id: ids.join(",") }).toString()
    const details = await youtubeJson(videoUrl, key)
    const items = Array.isArray(details.items) ? details.items : []
    videos = items.map((item: {
      id?: string
      snippet?: {
        title?: string
        channelTitle?: string
        publishedAt?: string
        thumbnails?: { medium?: { url?: string }; default?: { url?: string } }
      }
      statistics?: { viewCount?: string; likeCount?: string; commentCount?: string }
    }) => ({
      videoId: item.id ?? "",
      title: item.snippet?.title ?? "제목 없음",
      channelTitle: item.snippet?.channelTitle ?? "",
      thumbnailUrl: item.snippet?.thumbnails?.medium?.url ?? item.snippet?.thumbnails?.default?.url ?? null,
      publishedAt: item.snippet?.publishedAt ?? null,
      viewCount: item.statistics?.viewCount ?? null,
      likeCount: item.statistics?.likeCount ?? null,
      commentCount: item.statistics?.commentCount ?? null,
      videoUrl: `https://www.youtube.com/watch?v=${encodeURIComponent(item.id ?? "")}`,
    })).filter((video: RankedVideo) => video.videoId.length > 0)
    videos.sort((left, right) => {
      const leftViews = BigInt(left.viewCount ?? "0")
      const rightViews = BigInt(right.viewCount ?? "0")
      return rightViews > leftViews ? 1 : rightViews < leftViews ? -1 : 0
    })
  }
  return { category, query, rankingPolicy: "VIEW_COUNT_DESC", videos }
}

function cacheResult(row: CacheRow, status: "HIT" | "STALE", message?: string) {
  return json({
    ...row.payload,
    fetchedAt: row.fetched_at,
    expiresAt: row.expires_at,
    cacheStatus: status,
    message: message ?? null,
  })
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers })
  if (request.method !== "POST") return json({ message: "Method not allowed" }, 405)

  let category: RankingCategory
  try {
    const body = await request.json()
    if (body?.category !== "SQUAT" && body?.category !== "SHOULDER_PRESS") {
      return json({ message: "지원하지 않는 운동 카테고리입니다." }, 400)
    }
    category = body.category
  } catch {
    return json({ message: "올바른 요청 본문이 필요합니다." }, 400)
  }

  const query = RANKING_QUERIES[category]
  let cached: CacheRow | null
  try {
    cached = await readCache(category)
  } catch {
    return json({ message: "순위 캐시를 확인할 수 없습니다." }, 503)
  }
  if (cached?.query === query && Date.parse(cached.expires_at) > Date.now()) {
    return cacheResult(cached, "HIT")
  }
  const stale = cached?.query === query ? cached : null

  const key = Deno.env.get("YOUTUBE_API_KEY")
  if (!key) {
    return stale ? cacheResult(stale, "STALE", "최신 조회를 사용할 수 없습니다.")
      : json({ message: "YouTube 조회가 설정되지 않았습니다." }, 503)
  }

  let remaining: number
  try {
    remaining = await reserveSearchCall()
  } catch {
    return stale ? cacheResult(stale, "STALE", "최신 조회를 사용할 수 없습니다.")
      : json({ message: "무료 조회 한도를 확인할 수 없습니다." }, 503)
  }
  if (remaining < 0) {
    return stale ? cacheResult(stale, "STALE", QUOTA_MESSAGE)
      : json({ message: QUOTA_MESSAGE }, 429)
  }

  try {
    const ranking = await fetchRanking(category, query, key)
    const fetchedAt = new Date().toISOString()
    const expiresAt = new Date(Date.now() + CACHE_TTL_HOURS * 60 * 60 * 1000).toISOString()
    await writeCache(ranking, fetchedAt, expiresAt)
    return json({ ...ranking, fetchedAt, expiresAt, cacheStatus: "MISS", remainingSearchCalls: remaining })
  } catch (error) {
    const message = error instanceof QuotaExceededError ? QUOTA_MESSAGE : "최신 조회를 사용할 수 없습니다."
    return stale ? cacheResult(stale, "STALE", message)
      : json({ message }, error instanceof QuotaExceededError ? 429 : 502)
  }
})
