import { api } from './request'
import type { StatsOverview } from '@/types/model'

/** 首页汇总统计 */
export function getStatsOverview() {
  return api<StatsOverview>({ url: '/stats/overview', method: 'get' })
}
