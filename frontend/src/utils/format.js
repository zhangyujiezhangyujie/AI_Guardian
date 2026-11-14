/** 毫秒转为秒，保留 1 位小数（任务卡 6.5 TimingBar）；null 表示未执行。 */
export function formatSeconds(ms) {
  if (ms === null || ms === undefined) {
    return '—'
  }
  return `${(ms / 1000).toFixed(1)} 秒`
}
