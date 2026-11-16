/**
 * AnswerCard 的展示规则（任务卡 6.5 表格）。
 *
 * 只依据 guard.action、guard.answerVisible、verification.insufficientClaims、verification.truncated，
 * 不计算比例、不比较阈值、不判断结论真假。
 *
 * 返回：
 * - bannerType：el-alert 的 type（success / warning / error / info）
 * - body：'highlight' 按下标高亮 | 'unverified' 不高亮且灰色边框 | 'plain' 不高亮 | 'hidden' 不显示正文
 * - placeholder：body=hidden 时的占位文案
 * - showClaims：是否显示结论表（NO_EVIDENCE 不显示）
 */

export const PLACEHOLDER_BLOCKED = '回答已被拦截'

export const PLACEHOLDER_NOT_VERIFIED = '回答未经核验，已暂停展示'

export const PLACEHOLDER_HIDDEN = '回答未展示'

export function resolveAnswerView(response) {
  const guard = response.guard
  const verification = response.verification
  const action = guard.action

  // 失败即关闭：只要后端说不可见，无论 action 是什么都不显示正文
  if (action === 'BLOCKED') {
    return {
      bannerType: 'error',
      body: 'hidden',
      placeholder: PLACEHOLDER_BLOCKED,
      showClaims: true
    }
  }

  if (action === 'NOT_VERIFIED') {
    return guard.answerVisible
      ? {
        bannerType: 'error',
        body: 'unverified',
        placeholder: null,
        showClaims: true
      }
      : {
        bannerType: 'error',
        body: 'hidden',
        placeholder: PLACEHOLDER_NOT_VERIFIED,
        showClaims: true
      }
  }

  if (!guard.answerVisible) {
    return {
      bannerType: 'error',
      body: 'hidden',
      placeholder: PLACEHOLDER_HIDDEN,
      showClaims: true
    }
  }

  if (action === 'ANNOTATED') {
    const warn = verification.insufficientClaims > 0 || verification.truncated
    return {
      bannerType: warn ? 'warning' : 'success',
      body: 'highlight',
      placeholder: null,
      showClaims: true
    }
  }

  if (action === 'NO_EVIDENCE') {
    return {
      bannerType: 'info',
      body: 'plain',
      placeholder: null,
      showClaims: false
    }
  }

  // NO_CLAIMS 及未知的新 action：显示正文但不高亮
  return {
    bannerType: 'info',
    body: 'plain',
    placeholder: null,
    showClaims: true
  }
}
