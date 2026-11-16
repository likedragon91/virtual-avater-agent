const { contextBridge, ipcRenderer } = require('electron')

// 只暴露白名单能力，渲染进程不接触 Node / ipcRenderer 本体
contextBridge.exposeInMainWorld('desktopBridge', {
  isElectron: true,
  platform: process.platform,
  expand: () => ipcRenderer.send('window:expand'),
  collapse: () => ipcRenderer.send('window:collapse'),
  notify: (payload) => ipcRenderer.send('notify', payload),
  onNotificationClick: (callback) => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, payload) => callback(payload)
    ipcRenderer.on('notification:click', listener)
    return () => ipcRenderer.removeListener('notification:click', listener)
  },
  secureStore: {
    get: (key) => ipcRenderer.invoke('secure:get', key),
    set: (key, value) => ipcRenderer.invoke('secure:set', key, value),
    remove: (key) => ipcRenderer.invoke('secure:remove', key)
  }
})
