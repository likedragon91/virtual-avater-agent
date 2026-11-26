const { contextBridge, ipcRenderer } = require('electron')

// 只暴露白名单能力，渲染进程不接触 Node / ipcRenderer 本体
contextBridge.exposeInMainWorld('desktopBridge', {
  isElectron: true,
  platform: process.platform,
  appVersion: () => ipcRenderer.invoke('app:version'),
  openExternal: (url) => ipcRenderer.invoke('app:open-external', url),
  expand: () => ipcRenderer.send('window:expand'),
  collapse: () => ipcRenderer.send('window:collapse'),
  // 悬浮球自定义拖动：避免拖拽区吞掉右键菜单
  startBallDrag: (payload) => ipcRenderer.send('ball:drag-start', payload),
  moveBallDrag: (payload) => ipcRenderer.send('ball:drag-move', payload),
  endBallDrag: () => ipcRenderer.send('ball:drag-end'),
  notify: (payload) => ipcRenderer.send('notify', payload),
  // 主窗口保存形象后通知悬浮球刷新
  notifyAvatarUpdated: () => ipcRenderer.send('avatar:updated'),
  onAvatarUpdated: (callback) => {
    if (typeof callback !== 'function') return () => {}
    const listener = () => callback()
    ipcRenderer.on('avatar:updated', listener)
    return () => ipcRenderer.removeListener('avatar:updated', listener)
  },
  // 把当前台词同步到悬浮球，像歌词一样滚动
  sendBallLyric: (payload) => ipcRenderer.send('ball:lyric', payload),
  clearBallLyrics: () => ipcRenderer.send('ball:lyric-clear'),
  onBallLyric: (callback) => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, payload) => callback(payload)
    ipcRenderer.on('ball:lyric', listener)
    return () => ipcRenderer.removeListener('ball:lyric', listener)
  },
  onBallLyricClear: (callback) => {
    if (typeof callback !== 'function') return () => {}
    const listener = () => callback()
    ipcRenderer.on('ball:lyric-clear', listener)
    return () => ipcRenderer.removeListener('ball:lyric-clear', listener)
  },
  // 角色上方只显示最新一条助手回复的气泡
  sendChatReply: (payload) => ipcRenderer.send('chat:reply', payload),
  hideReplyBubble: () => ipcRenderer.send('chat:reply', {}),
  onChatReply: (callback) => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, payload) => callback(payload)
    ipcRenderer.on('chat:reply', listener)
    return () => ipcRenderer.removeListener('chat:reply', listener)
  },
  // 左侧对话框模式（主窗口停靠在角色左侧）
  getPanelMode: () => ipcRenderer.invoke('window:get-panel-mode'),
  // 首帧同步读取，避免面板窗口先闪一下当前路由页面
  getPanelModeSync: () => ipcRenderer.sendSync('window:get-panel-mode-sync'),
  onPanelMode: (callback) => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, value) => callback(value)
    ipcRenderer.on('window:panel-mode', listener)
    return () => ipcRenderer.removeListener('window:panel-mode', listener)
  },
  // 悬浮球形态：全身 / 大头
  getBallMode: () => ipcRenderer.invoke('ball:get-mode'),
  onBallMode: (callback) => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, mode) => callback(mode)
    ipcRenderer.on('ball:mode', listener)
    return () => ipcRenderer.removeListener('ball:mode', listener)
  },
  // 任务到点等提醒：角色头顶气泡
  sendBallBubble: (payload) => ipcRenderer.send('ball:bubble', payload),
  clearBallBubble: () => ipcRenderer.send('ball:bubble-clear'),
  onBallBubble: (callback) => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, payload) => callback(payload)
    ipcRenderer.on('ball:bubble', listener)
    return () => ipcRenderer.removeListener('ball:bubble', listener)
  },
  onBallBubbleClear: (callback) => {
    if (typeof callback !== 'function') return () => {}
    const listener = () => callback()
    ipcRenderer.on('ball:bubble-clear', listener)
    return () => ipcRenderer.removeListener('ball:bubble-clear', listener)
  },
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
