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

/**
 * 统一文件下载入口：响应是原始二进制流，不走 { code, msg, data } 解包。
 * 服务端业务失败时仍会返回 JSON 错误体（HTTP 200），此处识别后按错误提示抛出。
 */
export async function apiDownload(config: AxiosRequestConfig): Promise<Blob> {
  const response = await instance.request<Blob>({ ...config, responseType: 'blob' })
  const blob = response.data
  if (blob.type.includes('application/json')) {
    let msg = '下载失败'
    try {
      msg = (JSON.parse(await blob.text()) as ApiBody<unknown>).msg || msg
    } catch {
      // 错误体不是预期 JSON 时使用默认提示
    }
    ElMessage.error(msg)
    throw new Error(msg)
  }
  return blob
}
