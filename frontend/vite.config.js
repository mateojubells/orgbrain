import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En desarrollo, /api se redirige al backend Spring Boot (evita problemas de CORS).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.VITE_BACKEND_URL || 'http://localhost:8080',
    },
  },
})
