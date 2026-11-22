/**
 * 时间显示与转换。统一按 Asia/Shanghai 显示（契约 1：时间统一 Asia/Shanghai），
 * 与浏览器所在时区、后端返回的偏移量写法（+08:00 或 Z）无关。
 */

const TIME_ZONE = 'Asia/Shanghai'

const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000

function parts(iso) {
  const date = new Date(iso)
  if (typeof iso !== 'string' || Number.isNaN(date.getTime())) {
    return null
  }
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23'
  })
  const map = {}
  formatter.formatToParts(date).forEach((p) => {
    map[p.type] = p.value
  })
  map.ms = String(date.getUTCMilliseconds()).padStart(3, '0')
  return map
}

/** 列表用：11-19 20:43:05。无法解析时原样返回。 */
export function formatShortTime(iso) {
  const p = parts(iso)
  if (!p) {
    return iso ?? '—'
  }
  return `${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}`
}

/** 详情用：2026-11-19 20:43:05.512。 */
export function formatFullTime(iso) {
  const p = parts(iso)
  if (!p) {
    return iso ?? '—'
  }
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}.${p.ms}`
}

/** 时间线用：20:43:05.512。 */
export function formatClockTime(iso) {
  const p = parts(iso)
  if (!p) {
    return iso ?? '—'
  }
  return `${p.hour}:${p.minute}:${p.second}.${p.ms}`
}

/** 把一个时刻转为带 +08:00 偏移的 ISO-8601 字符串（审计查询参数 from / to，契约 7.8）。 */
export function toShanghaiIso(date) {
  const shifted = new Date(date.getTime() + SHANGHAI_OFFSET_MS)
  return shifted.toISOString().replace('Z', '+08:00')
}
