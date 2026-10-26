<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getStatsOverview } from '@/api/stats'
import { formatNumber } from '@/utils/format'
import type { StatsOverview } from '@/types/model'

const router = useRouter()
const stats = ref<StatsOverview | null>(null)
const loading = ref(false)

async function loadStats(): Promise<void> {
  loading.value = true
  try {
    stats.value = await getStatsOverview()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStats()
})

const cards = [
  { key: 'connectionCount', label: '数据库连接', icon: 'Connection', color: '#2563eb' },
  { key: 'taskCount', label: '同步任务', icon: 'Switch', color: '#059669' },
  { key: 'scheduleCount', label: '定时任务', icon: 'Timer', color: '#d97706' },
  { key: 'todayRecordCount', label: '今日执行', icon: 'Tickets', color: '#7c3aed' },
] as const

const guides = [
  {
    title: '1. 配置数据库连接',
    description: '在「连接管理」新增源库/目标库连接，支持 MySQL、Oracle、达梦、金仓、PostgreSQL，保存前可测试连通性。',
    path: '/connection',
  },
  {
    title: '2. 创建同步任务',
    description: '在「同步任务」选择源/目标连接，配置表映射与字段映射（支持字段名不一致与类型自动转换），即可手动执行。',
    path: '/task',
  },
  {
    title: '3. 结构对比（可选）',
    description: '在「结构对比」选择基准库与对比库，自动生成表级/字段级差异清单与 DDL 升级脚本，可在线预览或下载。',
    path: '/compare',
  },
  {
    title: '4. 定时自动执行（可选）',
    description: '在「定时任务」为同步任务配置 Cron，任务自动注册到 XXL-Job 调度中心，支持控制台手动触发与日志查看。',
    path: '/schedule',
  },
]
</script>

<template>
  <div v-loading="loading">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.key" :xs="12" :sm="12" :md="6">
        <div class="stat-card page-card">
          <div class="stat-icon" :style="{ backgroundColor: card.color }">
            <el-icon :size="22"><component :is="card.icon" /></el-icon>
          </div>
          <div>
            <div class="stat-value">
              {{ formatNumber(stats ? Number(stats[card.key]) : 0) }}
            </div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 今日执行情况 -->
    <div class="page-card today-card">
      <div class="today-item">
        <span class="today-label">今日成功</span>
        <span class="today-value success">{{ formatNumber(stats?.todaySuccessCount) }}</span>
      </div>
      <el-divider direction="vertical" />
      <div class="today-item">
        <span class="today-label">今日失败</span>
        <span class="today-value danger">{{ formatNumber(stats?.todayFailCount) }}</span>
      </div>
      <el-divider direction="vertical" />
      <div class="today-item today-rate">
        <span class="today-label">今日成功率</span>
        <el-progress
          :percentage="stats?.successRate ?? 0"
          :stroke-width="10"
          class="today-progress"
        />
      </div>
    </div>

    <!-- 使用指引 -->
    <div class="page-card">
      <div class="section-title">快速开始</div>
      <el-row :gutter="16">
        <el-col v-for="guide in guides" :key="guide.title" :xs="24" :sm="12" :md="12" :lg="6">
          <div class="guide-card" @click="router.push(guide.path)">
            <div class="guide-title">{{ guide.title }}</div>
            <div class="guide-desc">{{ guide.description }}</div>
            <div class="guide-link">前往配置<el-icon><ArrowRight /></el-icon></div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-top: 2px;
}

.today-card {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.today-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 8px;
}

.today-label {
  color: #64748b;
  font-size: 13px;
}

.today-value {
  font-size: 20px;
  font-weight: 700;
}

.today-value.success {
  color: #059669;
}

.today-value.danger {
  color: #dc2626;
}

.today-rate {
  flex: 1;
}

.today-progress {
  flex: 1;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 14px;
}

.guide-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  height: 100%;
  cursor: pointer;
  transition: all 0.2s;
  box-sizing: border-box;
}

.guide-card:hover {
  border-color: #2563eb;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.12);
  transform: translateY(-2px);
}

.guide-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.guide-desc {
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
  min-height: 66px;
}

.guide-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #2563eb;
  font-size: 13px;
  margin-top: 8px;
}
</style>
