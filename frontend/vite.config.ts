import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      // 백엔드 API 프록시 설정
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      // WebSocket 프록시 설정
      '/ws': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        ws: true,
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  define: {
    // SockJS 호환성을 위한 global 객체 정의
    global: 'globalThis',
  }
})
