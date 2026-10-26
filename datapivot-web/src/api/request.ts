import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiBody } from '@/types/model'

/** Axios 实例：走 Vite 代理指向后端 8081 */
const instance = axios.create({
  baseURL: '/api',
  timeout: 120000,
})

/** HTTP 层异常统一提示（业务层异常由统一返回体 code 判定） */
instance.interceptors.response.use(
  (response) => response,
  (error: { message?: string }) => {
    ElMessage.error(error.message ?? '网络异常，请检查后端服务是否启动')
    return Promise.reject(error)
  },
)

/**
 * 统一请求入口：解包 { code, msg, data }，code !== 200 时提示并 reject
 */
export async function api<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await instance.request<ApiBody<T>>(config)
  const body = response.data
  if (body.code !== 200) {
    ElMessage.error(body.msg || '请求失败')
    return Promise.reject(new Error(body.msg || '请求失败'))
  }
  return body.data
}
