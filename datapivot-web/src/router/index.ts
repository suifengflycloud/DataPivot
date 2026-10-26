import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'home',
          component: () => import('@/views/home/HomeView.vue'),
          meta: { title: '首页', icon: 'Odometer' },
        },
        {
          path: 'connection',
          name: 'connection',
          component: () => import('@/views/connection/ConnectionManage.vue'),
          meta: { title: '连接管理', icon: 'Connection' },
        },
        {
          path: 'task',
          name: 'task',
          component: () => import('@/views/task/SyncTaskManage.vue'),
          meta: { title: '同步任务', icon: 'Switch' },
        },
        {
          path: 'compare',
          name: 'compare',
          component: () => import('@/views/compare/SchemaCompare.vue'),
          meta: { title: '结构对比', icon: 'DocumentCopy' },
        },
        {
          path: 'record',
          name: 'record',
          component: () => import('@/views/record/RecordList.vue'),
          meta: { title: '任务执行记录', icon: 'Tickets' },
        },
        {
          path: 'schedule',
          name: 'schedule',
          component: () => import('@/views/schedule/ScheduleManage.vue'),
          meta: { title: '定时任务', icon: 'Timer' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/home',
    },
  ],
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? 'DataPivot')} - DataPivot`
})

export default router
