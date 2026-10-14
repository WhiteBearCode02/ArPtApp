import type { WorkoutInput } from "./contracts.ts"

export interface LlmProvider {
  readonly available: boolean
  generateWorkoutReport(input: WorkoutInput, prompt: string, signal: AbortSignal): Promise<unknown>
}

/** No provider selected: no SDK, outbound provider fetch, key reading or paid fallback. */
export class UnavailableLlmProvider implements LlmProvider {
  readonly available = false
  async generateWorkoutReport(): Promise<never> { throw new Error("Provider unavailable") }
}

// Add an adapter only AFTER verifying zero-charge terms, privacy and user approval.
// Merely setting LLM_ENABLED=true or placing a key cannot activate any provider in this revision.
export function createProvider(): LlmProvider { return new UnavailableLlmProvider() }
