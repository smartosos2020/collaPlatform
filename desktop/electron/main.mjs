import { app, BrowserWindow } from 'electron'
import { fileURLToPath } from 'node:url'

const DEFAULT_WEB_URL = 'http://localhost:5173'
const PRELOAD_PATH = fileURLToPath(new URL('./preload.mjs', import.meta.url))

function createWindow() {
  const window = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 360,
    minHeight: 640,
    title: 'Colla Platform',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: PRELOAD_PATH,
    },
  })

  window.loadURL(process.env.COLLA_WEB_URL || DEFAULT_WEB_URL)
}

app.whenReady().then(() => {
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
