import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// Vite 配置：@ 别名 + 开发代理（后端 8081）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    open: false,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
      },
    },
  },
  build: {
    // Element Plus 全量引入体积较大，放宽分块告警阈值保证构建零告警
    chunkSizeWarningLimit: 2000,
  },
})
