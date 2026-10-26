<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isCollapse = ref(false)

const pageTitle = computed(() => String(route.meta.title ?? ''))

const menus = [
  { path: '/home', title: '首页', icon: 'Odometer' },
  { path: '/connection', title: '连接管理', icon: 'Connection' },
  { path: '/task', title: '同步任务', icon: 'Switch' },
  { path: '/compare', title: '结构对比', icon: 'DocumentCopy' },
  { path: '/record', title: '任务执行记录', icon: 'Tickets' },
  { path: '/schedule', title: '定时任务', icon: 'Timer' },
]
</script>

<template>
  <el-container class="app-shell">
    <!-- 深色侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="brand">
        <div class="brand-logo">DP</div>
        <transition name="fade">
          <span v-if="!isCollapse" class="brand-name">DataPivot</span>
        </transition>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        background-color="#0f172a"
        text-color="#94a3b8"
        active-text-color="#ffffff"
        class="sidebar-menu"
      >
        <el-menu-item v-for="menu in menus" :key="menu.path" :index="menu.path">
          <el-icon><component :is="menu.icon" /></el-icon>
          <template #title>{{ menu.title }}</template>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer" @click="isCollapse = !isCollapse">
        <el-icon size="16"><Fold v-if="!isCollapse" /><Expand v-else /></el-icon>
        <span v-if="!isCollapse">收起菜单</span>
      </div>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="topbar">
        <div class="topbar-title">{{ pageTitle }}</div>
        <div class="topbar-sub">多源数据库迁移与结构对比工具</div>
        <div class="spacer" />
        <el-tag type="primary" effect="light" round>Vue3 + Spring Boot3</el-tag>
      </el-header>

      <!-- 内容区 -->
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-shell {
  height: 100%;
}

.sidebar {
  display: flex;
  flex-direction: column;
  background: #0f172a;
  transition: width 0.2s;
  overflow: hidden;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 16px;
  flex-shrink: 0;
}

.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-name {
  color: #f8fafc;
  font-size: 17px;
  font-weight: 600;
  white-space: nowrap;
}

.sidebar-menu {
  border-right: none;
  flex: 1;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: #2563eb;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: #1e293b;
}

.sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 44px;
  color: #64748b;
  cursor: pointer;
  border-top: 1px solid #1e293b;
  font-size: 13px;
  flex-shrink: 0;
}

.sidebar-footer:hover {
  color: #cbd5e1;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  height: 56px;
}

.topbar-title {
  font-size: 17px;
  font-weight: 600;
  color: #0f172a;
}

.topbar-sub {
  font-size: 13px;
  color: #94a3b8;
}

.content {
  background: #f1f5f9;
  padding: 16px;
  overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
