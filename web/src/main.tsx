import React from 'react'
import ReactDOM from 'react-dom/client'
import 'antd/dist/reset.css'
import './index.css'

import { App } from './app/App'
import { registerServiceWorker } from './shared/pwa/registerServiceWorker'

registerServiceWorker()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
