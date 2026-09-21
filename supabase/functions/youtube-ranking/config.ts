export const MAX_DAILY_SEARCH_CALLS = 30
export const CACHE_TTL_HOURS = 3

export const RANKING_QUERIES = {
  SQUAT: "squat workout",
  SHOULDER_PRESS: "shoulder press workout",
} as const

export type RankingCategory = keyof typeof RANKING_QUERIES
