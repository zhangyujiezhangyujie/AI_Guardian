/**
 * Mock 路由表。路径不含 /api 前缀（与 http.js 的 baseURL 对应）。
 *
 * 第 2 步只提供策略配置；ChatResponse、审计、知识库、演示场景的 Mock 数据在第 3 步补充（任务卡 6.4）。
 */
export const mockRoutes = [
  {
    method: 'get',
    pattern: /^\/guard\/config$/,
    handler: () => ({
      status: 200,
      data: {
        strategy: 'annotate',
        blockThreshold: 0.5,
        strictForToolCall: true,
        availableStrategies: ['annotate', 'block']
      }
    })
  }
]
