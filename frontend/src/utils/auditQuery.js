/**
 * 审计列表的筛选条件在 URL、表单、接口参数三者之间转换（契约 7.8）。
 * 筛选条件和页码保存在 URL 中：从详情页返回时保持原样，也可以把链接发给别人。
 */

export const AUDIT_PAGE_SIZE = 20

const FILTER_KEYS = ['requestId', 'state', 'action', 'mode', 'from', 'to']

function single(value) {
  if (Array.isArray(value)) {
    return value[0] ?? ''
  }
  return value ?? ''
}

/** URL query → 表单（时间范围为 [Date, Date] 或 null）。 */
export function queryToForm(query) {
  const from = single(query.from)
  const to = single(query.to)
  const fromDate = from ? new Date(from) : null
  const toDate = to ? new Date(to) : null
  const validRange = fromDate && toDate && !Number.isNaN(fromDate.getTime()) && !Number.isNaN(toDate.getTime())
  return {
    requestId: single(query.requestId),
    state: single(query.state),
    action: single(query.action),
    mode: single(query.mode),
    range: validRange ? [fromDate, toDate] : null
  }
}

/** 表单 → URL query（只保留非空项；查询时页码回到 1，不写入 URL）。 */
export function formToQuery(form, toIso) {
  const query = {}
  const requestId = (form.requestId || '').trim()
  if (requestId) {
    query.requestId = requestId
  }
  ;['state', 'action', 'mode'].forEach((key) => {
    if (form[key]) {
      query[key] = form[key]
    }
  })
  if (Array.isArray(form.range) && form.range[0] && form.range[1]) {
    query.from = toIso(form.range[0])
    query.to = toIso(form.range[1])
  }
  return query
}

/** URL query → 页码（非法值回到 1）。 */
export function queryToPage(query) {
  const page = Number.parseInt(single(query.page), 10)
  return Number.isInteger(page) && page >= 1 ? page : 1
}

/** URL query → GET /api/audit 参数。 */
export function queryToApiParams(query) {
  const params = {
    page: queryToPage(query),
    size: AUDIT_PAGE_SIZE
  }
  FILTER_KEYS.forEach((key) => {
    const value = single(query[key])
    if (value) {
      params[key] = value
    }
  })
  return params
}
