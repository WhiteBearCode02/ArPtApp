export type ObjectValue = Record<string, unknown>
export function object(value: unknown): ObjectValue {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("Invalid object")
  return value as ObjectValue
}
export function exact(value: unknown, keys: string[], optional: string[] = []): ObjectValue {
  const obj = object(value)
  if (Object.keys(obj).some(k => !keys.includes(k) && !optional.includes(k)) || keys.some(k => !Object.hasOwn(obj, k))) throw new Error("Invalid fields")
  return obj
}
export function number(value: unknown, min: number, max: number, integer = false, nullable = false): void {
  if (nullable && value === null) return
  if (typeof value !== "number" || !Number.isFinite(value) || value < min || value > max || (integer && !Number.isInteger(value))) throw new Error("Invalid number")
}
export function list(value: unknown, max: number): unknown[] {
  if (!Array.isArray(value) || value.length > max) throw new Error("Invalid list")
  return value
}
export function member(value: unknown, values: readonly string[], nullable = false): void {
  if (nullable && value === null) return
  if (typeof value !== "string" || !values.includes(value)) throw new Error("Invalid enum")
}
