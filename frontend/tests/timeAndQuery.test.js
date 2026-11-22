import { describe, expect, it } from 'vitest'

import { formatClockTime, formatFullTime, formatShortTime, toShanghaiIso } from '../src/utils/time'
import { AUDIT_PAGE_SIZE, formToQuery, queryToApiParams, queryToForm, queryToPage } from '../src/utils/auditQuery'

describe('时间显示（统一 Asia/Shanghai）', () => {
  it('+08:00 与 Z 两种写法显示一致', () => {
    expect(formatShortTime('2026-11-19T20:43:05.512+08:00')).toBe('11-19 20:43:05')
    expect(formatShortTime('2026-11-19T12:43:05.512Z')).toBe('11-19 20:43:05')
    expect(formatFullTime('2026-11-19T12:43:05.512Z')).toBe('2026-11-19 20:43:05.512')
    expect(formatClockTime('2026-11-19T20:43:05.007+08:00')).toBe('20:43:05.007')
  })

  it('跨日：UTC 16:30 是上海次日 00:30', () => {
    expect(formatShortTime('2026-11-19T16:30:00.000Z')).toBe('11-20 00:30:00')
  })

  it('无法解析时原样返回，null 显示“—”', () => {
    expect(formatShortTime('not-a-time')).toBe('not-a-time')
    expect(formatFullTime(null)).toBe('—')
  })

  it('toShanghaiIso 生成带 +08:00 偏移的同一时刻', () => {
    const date = new Date('2026-11-19T16:30:00.000Z')
    const iso = toShanghaiIso(date)
    expect(iso).toBe('2026-11-20T00:30:00.000+08:00')
    expect(Date.parse(iso)).toBe(date.getTime())
  })
})

describe('审计筛选：URL ↔ 表单 ↔ 接口参数（契约 7.8）', () => {
  it('URL → 接口参数：只带非空项，每页 20 条', () => {
    expect(queryToApiParams({ page: '2', action: 'BLOCKED', mode: '', state: undefined })).toEqual({
      page: 2,
      size: AUDIT_PAGE_SIZE,
      action: 'BLOCKED'
    })
    expect(AUDIT_PAGE_SIZE).toBe(20)
  })

  it('非法页码回到 1', () => {
    expect(queryToPage({ page: '0' })).toBe(1)
    expect(queryToPage({ page: 'abc' })).toBe(1)
    expect(queryToPage({})).toBe(1)
  })

  it('表单 → URL：去掉空白 requestId 两端空格，时间范围转为 +08:00', () => {
    const form = {
      requestId: '  req_3f9a1c0b7d2e ',
      state: 'COMPLETED',
      action: '',
      mode: 'MOCK',
      range: [new Date('2026-11-18T16:00:00.000Z'), new Date('2026-11-19T16:00:00.000Z')]
    }
    expect(formToQuery(form, toShanghaiIso)).toEqual({
      requestId: 'req_3f9a1c0b7d2e',
      state: 'COMPLETED',
      mode: 'MOCK',
      from: '2026-11-19T00:00:00.000+08:00',
      to: '2026-11-20T00:00:00.000+08:00'
    })
  })

  it('URL → 表单 → URL 往返不变', () => {
    const query = {
      requestId: 'req_3f9a1c0b7d2e',
      action: 'BLOCKED',
      from: '2026-11-19T00:00:00.000+08:00',
      to: '2026-11-20T00:00:00.000+08:00'
    }
    expect(formToQuery(queryToForm(query), toShanghaiIso)).toEqual(query)
  })

  it('时间范围不完整或非法时忽略', () => {
    expect(queryToForm({ from: '2026-11-19T00:00:00.000+08:00' }).range).toBeNull()
    expect(queryToForm({ from: 'x', to: 'y' }).range).toBeNull()
  })
})
