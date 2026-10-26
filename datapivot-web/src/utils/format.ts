/** 通用格式化工具 */

/** ISO 时间 -> 'YYYY-MM-DD HH:mm:ss'，空值返回 '—' */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const pad = (n: number): string => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

/** 毫秒时长 -> 人读格式 */
export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) {
    return '—'
  }
  if (ms < 1000) {
    return `${ms}ms`
  }
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) {
    return `${seconds}s`
  }
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) {
    return `${minutes}m${seconds % 60}s`
  }
  const hours = Math.floor(minutes / 60)
  return `${hours}h${minutes % 60}m`
}

/** 数字千分位 */
export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '0'
  }
  return value.toLocaleString('zh-CN')
}
