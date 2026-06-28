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
          if (
            normalizedId.includes('/@tiptap/pm') ||
            normalizedId.includes('/prosemirror-')
          ) {
            return 'editor-pm'
          }
          if (
            normalizedId.includes('/@tiptap/extension-table') ||
            normalizedId.includes('/@tiptap/extension-gapcursor')
          ) {
            return 'editor-table'
          }
          if (
            normalizedId.includes('/@tiptap/extension-image') ||
            normalizedId.includes('/@tiptap/extension-file-handler')
          ) {
            return 'editor-media'
          }
          if (normalizedId.includes('/@tiptap/extension-drag-handle')) {
            return 'editor-drag'
          }
          if (normalizedId.includes('/@tiptap/extension-task')) {
            return 'editor-task'
          }
          if (
            normalizedId.includes('/@tiptap/extension-bubble-menu') ||
            normalizedId.includes('/@tiptap/extension-floating-menu')
          ) {
            return 'editor-menu'
          }
          if (
            normalizedId.includes('/@tiptap/extension-bullet-list') ||
            normalizedId.includes('/@tiptap/extension-ordered-list') ||
            normalizedId.includes('/@tiptap/extension-list-item') ||
            normalizedId.includes('/@tiptap/extension-list-keymap') ||
            normalizedId.includes('/@tiptap/extension-list')
          ) {
            return 'editor-list'
          }
          if (
            normalizedId.includes('/@tiptap/extension-bold') ||
            normalizedId.includes('/@tiptap/extension-code') ||
            normalizedId.includes('/@tiptap/extension-italic') ||
            normalizedId.includes('/@tiptap/extension-link') ||
            normalizedId.includes('/@tiptap/extension-strike') ||
            normalizedId.includes('/@tiptap/extension-underline')
          ) {
            return 'editor-mark'
          }
          if (
            normalizedId.includes('/@tiptap/extension-blockquote') ||
            normalizedId.includes('/@tiptap/extension-code-block') ||
            normalizedId.includes('/@tiptap/extension-document') ||
            normalizedId.includes('/@tiptap/extension-hard-break') ||
            normalizedId.includes('/@tiptap/extension-heading') ||
            normalizedId.includes('/@tiptap/extension-horizontal-rule') ||
            normalizedId.includes('/@tiptap/extension-paragraph') ||
            normalizedId.includes('/@tiptap/extension-text')
          ) {
            return 'editor-block'
          }
          if (
            normalizedId.includes('/@tiptap/extension-dropcursor') ||
            normalizedId.includes('/@tiptap/extensions')
          ) {
            return 'editor-runtime'
          }
          if (normalizedId.includes('/@tiptap/react')) {
            return 'editor-react'
          }
          if (normalizedId.includes('/@tiptap/starter-kit')) {
            return 'editor-starter'
          }
          if (normalizedId.includes('/@tiptap/core')) {
            return 'editor-core'
          }
          if (normalizedId.includes('/@tiptap')) {
            return 'editor-core'
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
