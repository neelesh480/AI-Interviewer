import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/upload': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/analyze': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/generate': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/analyze-code': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})