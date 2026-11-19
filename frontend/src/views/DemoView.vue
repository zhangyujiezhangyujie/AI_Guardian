<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Collection, Loading } from '@element-plus/icons-vue'

import { listScenarios } from '@/api/demo'
import { getGuardConfig } from '@/api/guard'
import DemoRunResult from '@/components/DemoRunResult.vue'
import KnowledgeDrawer from '@/components/KnowledgeDrawer.vue'
import { GUARD_STRATEGY, SCENARIO_PRIORITY, labelText, labelType } from '@/constants/labels'
import {
  demoSession,
  runCompare,
  runSelected,
  selectScenario
} from '@/stores/demoSession'

/**
 * 页面三：演示场景（任务卡 6.8、契约 7.6、7.7）。
 * 左栏场景列表；右栏运行面板与结果区；右上角知识库抽屉。
 */

const scenarios = ref([])
const scenariosLoading = ref(false)
const scenariosError = ref('')
const guardConfig = ref(null)
const knowledgeVisible = ref(false)
const now = ref(Date.now())

let timer = null

const selected = computed(() => {
  return scenarios.value.find((s) => s.scenarioId === demoSession.selectedId) || null
})

const strategyOptions = computed(() => {
  const available = guardConfig.value?.availableStrategies ?? []
  return available.map((name) => ({
    value: name,
    label: labelText(GUARD_STRATEGY, name)
  }))
})

const defaultStrategyLabel = computed(() => {
  const name = guardConfig.value?.strategy
  return name ? `使用默认（${name}）` : '使用默认'
})

const thresholdPlaceholder = computed(() => {
  const value = guardConfig.value?.blockThreshold
  return value === undefined || value === null ? '留空为默认' : `默认 ${value}`
})

const elapsedSeconds = computed(() => {
  if (!demoSession.runningSince) {
    return 0
  }
  return Math.max(0, Math.floor((now.value - demoSession.runningSince) / 1000))
})

async function loadScenarios() {
  scenariosLoading.value = true
  scenariosError.value = ''
  try {
    scenarios.value = await listScenarios()
    const stillExists = scenarios.value.some((s) => s.scenarioId === demoSession.selectedId)
    if (!stillExists && scenarios.value.length > 0) {
      selectScenario(scenarios.value[0])
    }
  } catch (error) {
    scenariosError.value = error.message
  } finally {
    scenariosLoading.value = false
  }
}

async function loadGuardConfig() {
  try {
    guardConfig.value = await getGuardConfig()
  } catch (error) {
    guardConfig.value = null
  }
}

function choose(scenario) {
  if (demoSession.running || scenario.scenarioId === demoSession.selectedId) {
    return
  }
  selectScenario(scenario)
}

onMounted(() => {
  loadScenarios()
  loadGuardConfig()
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  clearInterval(timer)
})
</script>

<template>
  <div class="page-container demo-view">
    <div class="demo-header">
      <h2 class="page-title">
        演示场景
      </h2>
      <el-button
        :icon="Collection"
        @click="knowledgeVisible = true"
      >
        知识库
      </el-button>
    </div>

    <div class="demo-grid">
      <!-- 左栏：场景列表 -->
      <aside
        v-loading="scenariosLoading"
        class="scenario-list"
      >
        <el-result
          v-if="scenariosError"
          icon="error"
          title="场景加载失败"
          :sub-title="scenariosError"
        >
          <template #extra>
            <el-button
              type="primary"
              @click="loadScenarios"
            >
              重试
            </el-button>
          </template>
        </el-result>

        <button
          v-for="scenario in scenarios"
          :key="scenario.scenarioId"
          type="button"
          class="scenario-card"
          :class="{ 'is-active': scenario.scenarioId === demoSession.selectedId }"
          :disabled="demoSession.running && scenario.scenarioId !== demoSession.selectedId"
          @click="choose(scenario)"
        >
          <span class="scenario-card__head">
            <span class="scenario-card__id">{{ scenario.scenarioId }}</span>
            <span class="scenario-card__name">{{ scenario.name }}</span>
            <el-tag
              v-if="scenario.priority === 'P1'"
              :type="labelType(SCENARIO_PRIORITY, scenario.priority)"
              size="small"
              disable-transitions
            >
              {{ labelText(SCENARIO_PRIORITY, scenario.priority) }}
            </el-tag>
          </span>
          <span class="scenario-card__desc">{{ scenario.description }}</span>
        </button>
      </aside>

      <!-- 右栏：运行面板 + 结果 -->
      <section class="demo-main">
        <el-card
          v-if="selected"
          class="run-panel"
          shadow="never"
        >
          <div class="run-panel__question">
            <span class="field-label">问题</span>
            <span class="run-panel__question-text">{{ selected.question }}</span>
          </div>

          <el-form
            label-position="top"
            class="run-panel__form"
          >
            <el-form-item label="模式">
              <el-radio-group
                v-model="demoSession.mode"
                :disabled="demoSession.running"
              >
                <el-radio-button value="MOCK">
                  模拟
                </el-radio-button>
                <el-tooltip
                  :disabled="selected.supportsReal"
                  content="该场景依赖模拟的模型输出，不支持真实模式"
                  placement="top"
                >
                  <el-radio-button
                    value="REAL"
                    :disabled="!selected.supportsReal"
                  >
                    真实
                  </el-radio-button>
                </el-tooltip>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="输出策略">
              <!-- 空字符串表示“使用默认”（请求中 guardStrategy=null）；Element Plus 视空值为未选择，故用占位文字显示 -->
              <el-select
                v-model="demoSession.strategy"
                :placeholder="defaultStrategyLabel"
                :disabled="demoSession.running"
                class="run-panel__strategy"
              >
                <el-option
                  value=""
                  :label="defaultStrategyLabel"
                />
                <el-option
                  v-for="option in strategyOptions"
                  :key="option.value"
                  :value="option.value"
                  :label="option.label"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="拦截阈值">
              <el-input-number
                v-model="demoSession.threshold"
                :min="0"
                :max="1"
                :step="0.1"
                :precision="2"
                :value-on-clear="null"
                :placeholder="thresholdPlaceholder"
                :disabled="demoSession.running"
                controls-position="right"
                class="run-panel__threshold"
              />
            </el-form-item>
          </el-form>

          <div class="run-panel__actions">
            <el-button
              type="primary"
              :loading="demoSession.running"
              @click="runSelected"
            >
              运行
            </el-button>
            <el-button
              :disabled="demoSession.running"
              @click="runCompare"
            >
              两种策略对比运行
            </el-button>
            <span
              v-if="selected.recommendedStrategies && selected.recommendedStrategies.length"
              class="run-panel__hint"
            >
              推荐策略：{{ selected.recommendedStrategies.join('、') }}
            </span>
          </div>
        </el-card>

        <el-card
          v-if="demoSession.running"
          class="run-loading"
          shadow="never"
        >
          <el-icon class="is-loading">
            <Loading />
          </el-icon>
          <span>{{ demoSession.runningLabel }}：检索、生成与核验中</span>
          <span class="run-loading__elapsed">已等待 {{ elapsedSeconds }} 秒</span>
        </el-card>

        <template v-else-if="demoSession.results">
          <DemoRunResult
            v-if="demoSession.results.kind === 'single'"
            :run="demoSession.results.runs[0]"
          />
          <div
            v-else
            class="compare-grid"
          >
            <DemoRunResult
              v-for="run in demoSession.results.runs"
              :key="run.strategy"
              :run="run"
              show-strategy
            />
          </div>
        </template>

        <el-empty
          v-else-if="selected"
          :image-size="80"
          description="选择策略后点击“运行”，或点击“两种策略对比运行”"
        />
      </section>
    </div>

    <KnowledgeDrawer v-model="knowledgeVisible" />
  </div>
</template>

<style scoped>
.demo-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.demo-grid {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.scenario-list {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 120px;
}

.scenario-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 10px 12px;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background-color: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  transition: border-color 0.15s, background-color 0.15s;
}

.scenario-card:hover:not(:disabled) {
  border-color: var(--el-color-primary-light-5);
}

.scenario-card.is-active {
  background-color: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  box-shadow: inset 3px 0 0 var(--el-color-primary);
}

.scenario-card:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.scenario-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scenario-card__id {
  flex: none;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.scenario-card__name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.scenario-card__desc {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.demo-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.run-panel__question {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  font-size: 16px;
  line-height: 1.7;
}

.field-label {
  flex: none;
  color: var(--el-text-color-secondary);
}

.run-panel__question-text {
  font-weight: 600;
}

.run-panel__form {
  display: flex;
  flex-wrap: wrap;
  gap: 0 24px;
}

.run-panel__form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.run-panel__strategy {
  width: 200px;
}

.run-panel__threshold {
  width: 140px;
}

.run-panel__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.run-panel__actions .el-button + .el-button {
  margin-left: 0;
}

.run-panel__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.run-loading :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
}

.run-loading__elapsed {
  margin-left: auto;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-items: start;
}
</style>
