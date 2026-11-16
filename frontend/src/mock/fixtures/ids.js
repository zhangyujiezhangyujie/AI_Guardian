/**
 * Mock 用的 ID 与时间工具（格式按契约 3.7、13.1）。
 */

function randomHex(length) {
  let result = ''
  for (let i = 0; i < length; i++) {
    result += Math.floor(Math.random() * 16).toString(16)
  }
  return result
}

export function newRequestId() {
  return `req_${randomHex(12)}`
}

export function newSessionId() {
  return `ses_${randomHex(12)}`
}

const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000

/** 转为 Asia/Shanghai 的 ISO-8601 字符串，例如 2026-11-20T14:03:21.512+08:00。 */
export function shanghaiIso(date) {
  const shifted = new Date(date.getTime() + SHANGHAI_OFFSET_MS)
  return shifted.toISOString().replace('Z', '+08:00')
}
