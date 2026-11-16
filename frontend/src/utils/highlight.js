/**
 * 高亮算法（任务卡 6.5 HighlightedAnswer）。
 *
 * 1. 取 startIndex、endIndex 都非空的结论，按 startIndex 升序排列；
 * 2. 依次处理，与前一个已采用的区间重叠则跳过（该结论仍出现在结论表中）；
 * 3. 把文本切成“普通文本 / 结论文本”交替的片段数组。
 *
 * 下标是后端给出的 Java UTF-16 下标，与 JS 字符串下标一致，直接 slice。
 * 防御性检查：下标越界或对应文本与 originalText 不一致的结论不高亮，避免标错位置。
 *
 * 返回 [{ text, claim, number }]：claim 为 null 表示普通文本；number 为结论在 claims 中的序号（从 1 开始，与结论表一致）。
 */
export function buildSegments(text, claims) {
  if (typeof text !== 'string' || text.length === 0) {
    return []
  }

  const candidates = (claims || [])
    .map((claim, index) => ({
      claim,
      number: index + 1
    }))
    .filter(({ claim }) => isLocatable(text, claim))
    .sort((a, b) => a.claim.startIndex - b.claim.startIndex)

  const accepted = []
  let lastEnd = 0
  candidates.forEach((candidate) => {
    if (candidate.claim.startIndex < lastEnd) {
      return
    }
    accepted.push(candidate)
    lastEnd = candidate.claim.endIndex
  })

  const segments = []
  let cursor = 0
  accepted.forEach(({ claim, number }) => {
    if (claim.startIndex > cursor) {
      segments.push({
        text: text.slice(cursor, claim.startIndex),
        claim: null,
        number: null
      })
    }
    segments.push({
      text: text.slice(claim.startIndex, claim.endIndex),
      claim,
      number
    })
    cursor = claim.endIndex
  })
  if (cursor < text.length) {
    segments.push({
      text: text.slice(cursor),
      claim: null,
      number: null
    })
  }
  return segments
}

function isLocatable(text, claim) {
  if (!claim) {
    return false
  }
  const { startIndex, endIndex, originalText } = claim
  if (!Number.isInteger(startIndex) || !Number.isInteger(endIndex)) {
    return false
  }
  if (startIndex < 0 || endIndex > text.length || startIndex >= endIndex) {
    return false
  }
  return text.slice(startIndex, endIndex) === originalText
}

/**
 * 证据抽屉中的 quote 高亮：content.indexOf(quote) >= 0 时切成三段，找不到则不高亮（不做模糊匹配）。
 * 返回 [{ text, highlight }]。
 */
export function splitByQuote(content, quote) {
  if (typeof content !== 'string') {
    return []
  }
  if (typeof quote !== 'string' || quote.length === 0) {
    return [{ text: content, highlight: false }]
  }
  const position = content.indexOf(quote)
  if (position < 0) {
    return [{ text: content, highlight: false }]
  }
  return [
    { text: content.slice(0, position), highlight: false },
    { text: quote, highlight: true },
    { text: content.slice(position + quote.length), highlight: false }
  ].filter((part) => part.text.length > 0)
}
