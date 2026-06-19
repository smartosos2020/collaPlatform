import { contextBridge } from 'electron'

contextBridge.exposeInMainWorld('collaDesktop', {
  shell: 'electron',
})
