import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalizedId = id.replace(/\\/g, '/')
          if (!normalizedId.includes('node_modules')) {
            return undefined
          }

          if (
            normalizedId.includes('/react/') ||
            normalizedId.includes('/react-dom/') ||
            normalizedId.includes('/react-router-dom/')
          ) {
            return 'react'
          }

          const antdComponent = normalizedId.match(/node_modules\/antd\/es\/([^/]+)/)
          if (antdComponent) {
            return `antd-${antdComponent[1]}`
          }
          if (normalizedId.includes('/@ant-design/icons')) {
            return 'antd-icons'
          }
          if (normalizedId.includes('/@ant-design/')) {
            return 'antd-runtime'
          }
          if (normalizedId.includes('/rc-')) {
            const rcPackage = normalizedId.match(/node_modules\/(rc-[^/]+)/)
            return rcPackage ? rcPackage[1] : 'rc-vendor'
          }

          if (normalizedId.includes('/@tanstack')) {
            return 'query'
          }
          if (normalizedId.includes('/@tiptap')) {
            return 'editor'
          }
          if (normalizedId.includes('/zustand')) {
            return 'state'
          }
          return 'vendor'
        },
      },
    },
  },
})
