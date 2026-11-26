const {
  app,
  BrowserWindow,
  Menu,
  Notification,
  ipcMain,
  nativeImage,
  screen,
  shell,
  safeStorage
} = require('electron')
const fs = require('node:fs')
const path = require('node:path')

const APP_ID = 'com.example.avataragent'
const DEV_URL = 'http://127.0.0.1:5173'
const BALL_WIDTH = 280
const BALL_HEIGHT = 420
const HEAD_BALL_SIZE = 180
const BUBBLE_WIDTH = 280
const BUBBLE_HEIGHT = 180
const PANEL_WIDTH = 420
const PANEL_HEIGHT = 140
const PANEL_GAP = 8
const REPLY_WIDTH = 340
const REPLY_HEIGHT = 240
const REPLY_GAP = 8
const isDev = process.argv.includes('--dev') || !app.isPackaged
const SECURE_STORE_FILE = 'secure-store.json'
const ALLOWED_SECURE_KEYS = new Set(['refreshToken'])

let ballWindow = null
let bubbleWindow = null
let mainWindow = null
let isQuitting = false
let ballBubbleActive = false
let ballBubbleTimer = null
let pendingBubble = null
let ballMode = 'full'
let replyWindow = null
let lastReply = null
let ballDrag = null
let chatPanelOpen = false

// Windows 原生通知必须设置 AppUserModelID，且与 electron-builder 的 appId 一致
app.setAppUserModelId(APP_ID)

const gotSingleInstanceLock = app.requestSingleInstanceLock()
if (!gotSingleInstanceLock) {
  app.quit()
}

function iconPath() {
  return path.join(__dirname, 'assets', 'icon.ico')
}

function windowIcon() {
  const file = iconPath()
  return fs.existsSync(file) ? nativeImage.createFromPath(file) : undefined
}

function secureStorePath() {
  return path.join(app.getPath('userData'), SECURE_STORE_FILE)
}

function readSecureStore() {
  try {
    return JSON.parse(fs.readFileSync(secureStorePath(), 'utf8')) || {}
  } catch {
    return {}
  }
}

function writeSecureStore(data) {
  const file = secureStorePath()
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, JSON.stringify(data), { encoding: 'utf8', mode: 0o600 })
}

function hardenWindow(window) {
  window.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) {
      shell.openExternal(url)
    }
    return { action: 'deny' }
  })
  window.webContents.on('will-navigate', (event, url) => {
    const allowedDevOrigin = isDev && url.startsWith(DEV_URL)
    if (!allowedDevOrigin && !url.startsWith('file://')) {
      event.preventDefault()
      if (/^https?:\/\//i.test(url)) shell.openExternal(url)
    }
  })
}

function positionBallWindow() {
  if (!ballWindow || ballWindow.isDestroyed()) return
  const [width, height] = ballWindow.getSize()
  const { workArea } = screen.getDisplayMatching(ballWindow.getBounds())
  const margin = 24
  ballWindow.setPosition(
    Math.round(workArea.x + workArea.width - width - margin),
    Math.round(workArea.y + workArea.height - height - margin)
  )
}

/** 切换全身 / 大头，保持底部中心位置不变 */
function applyBallMode(mode) {
  ballMode = mode === 'head' ? 'head' : 'full'
  if (!ballWindow || ballWindow.isDestroyed()) return

  const old = ballWindow.getBounds()
  const width = ballMode === 'head' ? HEAD_BALL_SIZE : BALL_WIDTH
  const height = ballMode === 'head' ? HEAD_BALL_SIZE : BALL_HEIGHT
  const { workArea } = screen.getDisplayMatching(old)

  let x = Math.round(old.x + (old.width - width) / 2)
  let y = Math.round(old.y + (old.height - height))
  x = Math.max(workArea.x, Math.min(x, workArea.x + workArea.width - width))
  y = Math.max(workArea.y, Math.min(y, workArea.y + workArea.height - height))

  ballWindow.setBounds({ x, y, width, height }, false)
  ballWindow.webContents.send('ball:mode', ballMode)
  positionBubbleWindow()
  positionReplyBubbleWindow()
}

/** 左侧对话框：主窗口停靠在角色左侧，角色保持可见 */
function positionChatPanel() {
  if (!chatPanelOpen) return
  if (!mainWindow || mainWindow.isDestroyed()) return
  if (!ballWindow || ballWindow.isDestroyed() || !ballWindow.isVisible()) return

  const ball = ballWindow.getBounds()
  const { workArea } = screen.getDisplayMatching(ball)
  const width = PANEL_WIDTH
  const height = PANEL_HEIGHT
  let x = ball.x - width - PANEL_GAP
  let y = ball.y + Math.round((ball.height - height) / 2)
  x = Math.max(workArea.x, Math.min(x, workArea.x + workArea.width - width))
  y = Math.max(workArea.y, Math.min(y, workArea.y + workArea.height - height))

  // 只保留输入框：固定尺寸，拖动时不会被系统/Dpi 改大
  const current = mainWindow.getBounds()
  if (current.width !== width || current.height !== height) {
    mainWindow.setBounds({ x: Math.round(x), y: Math.round(y), width, height }, false)
  } else {
    mainWindow.setPosition(Math.round(x), Math.round(y), false)
  }
}

function openChatPanel() {
  // 先置位再创建窗口：新建窗口首帧同步读取面板状态时不会读到旧值
  const wasOpen = chatPanelOpen
  chatPanelOpen = true
  const win = createMainWindow({ showOnReady: false })
  if (!wasOpen) {
    win.setMinimumSize(360, 120)
    win.setResizable(false)
    win.setSize(PANEL_WIDTH, PANEL_HEIGHT)
  }
  positionChatPanel()
  const sendPanelMode = () => {
    if (win && !win.isDestroyed()) {
      win.webContents.send('window:panel-mode', true)
    }
  }
  if (win.webContents.isLoading()) {
    win.webContents.once('did-finish-load', sendPanelMode)
  } else {
    sendPanelMode()
  }
  win.show()
  win.focus()

  // 角色保持可见，气泡/对话框都以它为中心
  if (ballWindow && !ballWindow.isDestroyed()) {
    if (!ballWindow.isVisible()) ballWindow.show()
    positionBubbleWindow()
  }
  // 回复气泡由渲染进程在面板模式同步后按需显示
}

function closeChatPanel() {
  const wasOpen = chatPanelOpen
  chatPanelOpen = false
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('window:panel-mode', false)
    mainWindow.hide()
    if (wasOpen) restoreMainWindowSize(mainWindow)
  }
  hideReplyBubble()
  if (ballWindow && !ballWindow.isDestroyed() && !ballWindow.isVisible()) {
    showBall()
  }
}

/** 回复气泡：只显示最新一条助手回复，跟随在角色上方 */
function createReplyBubbleWindow() {
  if (replyWindow && !replyWindow.isDestroyed()) return replyWindow

  replyWindow = new BrowserWindow({
    width: REPLY_WIDTH,
    height: REPLY_HEIGHT,
    frame: false,
    transparent: true,
    resizable: false,
    movable: false,
    focusable: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    hasShadow: false,
    backgroundColor: '#00000000',
    type: 'toolbar',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      backgroundThrottling: false
    }
  })

  // 纯展示气泡，不拦截桌面点击
  replyWindow.setIgnoreMouseEvents(true)
  replyWindow.setAlwaysOnTop(true, 'floating')
  hardenWindow(replyWindow)
  replyWindow.loadFile(path.join(__dirname, 'history.html'))
  replyWindow.on('closed', () => {
    replyWindow = null
  })
  replyWindow.webContents.on('did-finish-load', () => {
    if (replyWindow && !replyWindow.isDestroyed() && lastReply) {
      replyWindow.webContents.send('chat:reply', lastReply)
    }
  })
  return replyWindow
}

function positionReplyBubbleWindow() {
  if (!replyWindow || replyWindow.isDestroyed()) return
  if (!ballWindow || ballWindow.isDestroyed() || !ballWindow.isVisible()) return

  const ball = ballWindow.getBounds()
  const { workArea } = screen.getDisplayMatching(ball)
  const width = REPLY_WIDTH
  const height = REPLY_HEIGHT
  let x = Math.round(ball.x + (ball.width - width) / 2)
  let y = ball.y - height - REPLY_GAP

  // 任务气泡也占用角色上方，回复气泡叠在它上方
  if (ballBubbleActive && bubbleWindow && !bubbleWindow.isDestroyed() && bubbleWindow.isVisible()) {
    y = Math.min(y, bubbleWindow.getBounds().y - height - REPLY_GAP)
  }

  x = Math.max(workArea.x, Math.min(x, workArea.x + workArea.width - width))
  y = Math.max(workArea.y, Math.min(y, workArea.y + workArea.height - height))

  const current = replyWindow.getBounds()
  if (current.width !== width || current.height !== height) {
    replyWindow.setBounds({ x, y, width, height }, false)
  } else {
    replyWindow.setPosition(x, y, false)
  }
}

function presentReplyBubble(payload) {
  lastReply = payload
  if (!chatPanelOpen) return

  if (!payload || (!payload.text && !payload.pending && !payload.error)) {
    hideReplyBubble()
    return
  }
  if (!ballWindow || ballWindow.isDestroyed() || !ballWindow.isVisible()) return

  const win = createReplyBubbleWindow()
  const send = () => {
    if (win && !win.isDestroyed()) win.webContents.send('chat:reply', payload)
  }
  if (win.webContents.isLoading()) {
    win.webContents.once('did-finish-load', send)
  } else {
    send()
  }
  positionReplyBubbleWindow()
  if (!win.isVisible()) win.showInactive()
}

function hideReplyBubble() {
  if (replyWindow && !replyWindow.isDestroyed()) {
    replyWindow.hide()
  }
}

/** 从左侧面板恢复成正常主窗口尺寸 */
function restoreMainWindowSize(win) {
  if (!win || win.isDestroyed()) return
  win.setResizable(true)
  win.setMinimumSize(900, 640)
  const { workArea } = screen.getPrimaryDisplay()
  const width = Math.min(1200, Math.max(900, workArea.width - 80))
  const height = Math.min(800, Math.max(640, workArea.height - 80))
  win.setSize(width, height)
  win.center()
}

function createBallWindow() {
  if (ballWindow && !ballWindow.isDestroyed()) {
    ballWindow.show()
    return ballWindow
  }

  ballWindow = new BrowserWindow({
    width: ballMode === 'head' ? HEAD_BALL_SIZE : BALL_WIDTH,
    height: ballMode === 'head' ? HEAD_BALL_SIZE : BALL_HEIGHT,
    frame: false,
    transparent: true,
    resizable: false,
    movable: true,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    hasShadow: false,
    backgroundColor: '#00000000',
    type: 'toolbar',
    icon: windowIcon(),
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      // 失焦后 Pixi / Live2D 仍需持续渲染
      backgroundThrottling: false
    }
  })

  ballWindow.setAlwaysOnTop(true, 'floating')
  positionBallWindow()
  hardenWindow(ballWindow)

  // 悬浮球没有关闭按钮，右键菜单提供展开与退出
  ballWindow.webContents.on('context-menu', () => {
    Menu.buildFromTemplate([
      { label: '展开虚拟形象', click: expandFromBall },
      {
        label: ballMode === 'head' ? '恢复全身' : '大头模式',
        click: () => applyBallMode(ballMode === 'head' ? 'full' : 'head')
      },
      {
        label: chatPanelOpen ? '关闭左侧对话框' : '左侧对话框',
        click: () => (chatPanelOpen ? closeChatPanel() : openChatPanel())
      },
      { type: 'separator' },
      {
        label: '退出',
        click: () => {
          isQuitting = true
          app.quit()
        }
      }
    ]).popup({ window: ballWindow })
  })

  if (isDev) {
    ballWindow.loadURL(`${DEV_URL}/ball.html`)
  } else {
    ballWindow.loadFile(path.join(__dirname, '..', 'dist-electron', 'ball.html'))
  }
  ballWindow.webContents.on('did-finish-load', () => {
    if (ballWindow && !ballWindow.isDestroyed()) {
      ballWindow.webContents.send('ball:mode', ballMode)
    }
  })
  ballWindow.on('move', () => {
    positionBubbleWindow()
    positionChatPanel()
    positionReplyBubbleWindow()
  })
  ballWindow.on('closed', () => {
    ballWindow = null
    hideBallBubble()
    if (bubbleWindow && !bubbleWindow.isDestroyed()) {
      bubbleWindow.close()
    }
  })
  return ballWindow
}

/** 让悬浮球重新拉取当前形象；窗口尚未加载完成时由 BallApp 挂载时自行拉取 */
function notifyBallAvatarUpdated() {
  if (ballWindow && !ballWindow.isDestroyed()) {
    ballWindow.webContents.send('avatar:updated')
  }
}

function hideBall() {
  if (ballWindow && !ballWindow.isDestroyed()) {
    ballWindow.hide()
  }
  hideBallBubble()
  hideReplyBubble()
}

function showBall() {
  if (!ballWindow || ballWindow.isDestroyed()) {
    createBallWindow()
  } else {
    positionBallWindow()
    ballWindow.show()
    notifyBallAvatarUpdated()
  }
  if (pendingBubble) {
    presentBallBubble(pendingBubble)
  }
}

/** 独立的气泡窗口：放在悬浮球正上方，点击穿透，不遮挡角色 */
function createBubbleWindow() {
  if (bubbleWindow && !bubbleWindow.isDestroyed()) return bubbleWindow

  bubbleWindow = new BrowserWindow({
    width: BUBBLE_WIDTH,
    height: BUBBLE_HEIGHT,
    frame: false,
    transparent: true,
    resizable: false,
    movable: false,
    focusable: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    hasShadow: false,
    backgroundColor: '#00000000',
    type: 'toolbar',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      backgroundThrottling: false
    }
  })

  bubbleWindow.setIgnoreMouseEvents(true)
  bubbleWindow.setAlwaysOnTop(true, 'floating')
  hardenWindow(bubbleWindow)
  bubbleWindow.loadFile(path.join(__dirname, 'bubble.html'))
  bubbleWindow.on('closed', () => {
    bubbleWindow = null
  })
  return bubbleWindow
}

function positionBubbleWindow() {
  if (!bubbleWindow || bubbleWindow.isDestroyed()) return
  if (!ballWindow || ballWindow.isDestroyed() || !ballWindow.isVisible()) return

  const ball = ballWindow.getBounds()
  const { workArea } = screen.getDisplayMatching(ball)
  const [width, height] = bubbleWindow.getSize()
  let x = Math.round(ball.x + (ball.width - width) / 2)
  let y = Math.round(ball.y - height - 8)
  x = Math.max(workArea.x, Math.min(x, workArea.x + workArea.width - width))
  y = Math.max(workArea.y, Math.min(y, workArea.y + workArea.height - height))
  bubbleWindow.setPosition(x, y, false)
}

function presentBallBubble(payload) {
  pendingBubble = null
  const win = createBubbleWindow()
  const present = () => {
    if (!win || win.isDestroyed()) return
    win.webContents.send('ball:bubble', payload)
    ballBubbleActive = true
    positionBubbleWindow()
    positionReplyBubbleWindow()
    win.showInactive()
    if (ballBubbleTimer) clearTimeout(ballBubbleTimer)
    ballBubbleTimer = setTimeout(() => {
      hideBallBubble()
    }, 12000)
  }
  if (win.webContents.isLoading()) {
    win.webContents.once('did-finish-load', present)
  } else {
    present()
  }
}

function showBallBubble(payload) {
  pendingBubble = payload
  if (!ballWindow || ballWindow.isDestroyed() || !ballWindow.isVisible()) {
    return
  }
  presentBallBubble(payload)
}

function hideBallBubble() {
  ballBubbleActive = false
  if (ballBubbleTimer) {
    clearTimeout(ballBubbleTimer)
    ballBubbleTimer = null
  }
  if (bubbleWindow && !bubbleWindow.isDestroyed()) {
    bubbleWindow.webContents.send('ball:bubble-clear')
    bubbleWindow.hide()
  }
  positionReplyBubbleWindow()
}

function createMainWindow({ showOnReady = true } = {}) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    if (showOnReady) {
      mainWindow.show()
      mainWindow.focus()
    }
    return mainWindow
  }

  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 900,
    minHeight: 640,
    show: false,
    title: '虚拟形象',
    icon: windowIcon(),
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      // 主窗口隐藏到悬浮球时仍要接收通知 SSE 与定时器
      backgroundThrottling: false
    }
  })

  hardenWindow(mainWindow)

  mainWindow.once('ready-to-show', () => {
    if (showOnReady) {
      mainWindow.show()
      mainWindow.focus()
    }
  })

  // 关闭主窗口时回到悬浮球，真正退出由 before-quit 驱动
  mainWindow.on('close', (event) => {
    if (isQuitting) return
    event.preventDefault()
    const wasPanel = chatPanelOpen
    chatPanelOpen = false
    if (wasPanel) {
      mainWindow.webContents.send('window:panel-mode', false)
      restoreMainWindowSize(mainWindow)
      hideReplyBubble()
    }
    mainWindow.hide()
    if (!ballWindow || ballWindow.isDestroyed() || !ballWindow.isVisible()) {
      showBall()
    }
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  if (isDev) {
    mainWindow.loadURL(DEV_URL)
  } else {
    mainWindow.loadFile(path.join(__dirname, '..', 'dist-electron', 'index.html'))
  }

  return mainWindow
}

function expandFromBall() {
  const wasPanel = chatPanelOpen
  if (wasPanel) {
    chatPanelOpen = false
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('window:panel-mode', false)
    }
  }
  hideBall()
  const win = createMainWindow()
  if (wasPanel) restoreMainWindowSize(win)
}

function collapseToBall() {
  if (chatPanelOpen) {
    chatPanelOpen = false
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('window:panel-mode', false)
      restoreMainWindowSize(mainWindow)
    }
    hideReplyBubble()
  }
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.hide()
  }
  showBall()
}

function registerIpc() {
  ipcMain.handle('app:version', () => app.getVersion())
  ipcMain.handle('ball:get-mode', () => ballMode)
  ipcMain.handle('window:get-panel-mode', () => chatPanelOpen)
  ipcMain.on('window:get-panel-mode-sync', (event) => {
    event.returnValue = chatPanelOpen
  })
  ipcMain.handle('app:open-external', async (_event, url) => {
    const value = String(url || '')
    if (!/^https?:\/\//i.test(value)) return false
    await shell.openExternal(value)
    return true
  })

  ipcMain.on('window:expand', expandFromBall)
  ipcMain.on('window:collapse', collapseToBall)

  // 悬浮球自定义拖动（角色区域同时支持右键菜单与点击展开）
  ipcMain.on('ball:drag-start', (_event, payload = {}) => {
    if (!ballWindow || ballWindow.isDestroyed()) return
    ballDrag = {
      screenX: Number(payload.screenX) || 0,
      screenY: Number(payload.screenY) || 0,
      bounds: ballWindow.getBounds()
    }
  })
  ipcMain.on('ball:drag-move', (_event, payload = {}) => {
    if (!ballDrag || !ballWindow || ballWindow.isDestroyed()) return
    const screenX = Number(payload.screenX) || 0
    const screenY = Number(payload.screenY) || 0
    const x = Math.round(ballDrag.bounds.x + screenX - ballDrag.screenX)
    const y = Math.round(ballDrag.bounds.y + screenY - ballDrag.screenY)

    // 拖动只改位置；同时校正尺寸，避免系统 DPI/边界行为把窗口放大
    const width = ballMode === 'head' ? HEAD_BALL_SIZE : BALL_WIDTH
    const height = ballMode === 'head' ? HEAD_BALL_SIZE : BALL_HEIGHT
    const current = ballWindow.getBounds()
    if (current.width !== width || current.height !== height) {
      ballWindow.setBounds({ x, y, width, height }, false)
    } else {
      ballWindow.setPosition(x, y, false)
    }
  })
  ipcMain.on('ball:drag-end', () => {
    ballDrag = null
  })
  ipcMain.on('avatar:updated', notifyBallAvatarUpdated)
  ipcMain.on('ball:lyric', (_event, payload) => {
    if (ballWindow && !ballWindow.isDestroyed()) {
      ballWindow.webContents.send('ball:lyric', payload)
    }
  })
  ipcMain.on('ball:lyric-clear', () => {
    if (ballWindow && !ballWindow.isDestroyed()) {
      ballWindow.webContents.send('ball:lyric-clear')
    }
  })
  ipcMain.on('chat:reply', (_event, payload) => {
    presentReplyBubble(payload)
  })
  ipcMain.on('ball:bubble', (_event, payload) => {
    showBallBubble(payload)
  })
  ipcMain.on('ball:bubble-clear', () => {
    hideBallBubble()
  })

  ipcMain.on('notify', (_event, payload = {}) => {
    if (!Notification.isSupported()) return
    const title = String(payload.title || '虚拟形象').slice(0, 120)
    const body = String(payload.body || '').slice(0, 500)
    const options = { title, body }
    const icon = windowIcon()
    if (icon) options.icon = icon

    const notification = new Notification(options)
    notification.on('click', () => {
      createMainWindow()
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('notification:click', {
          id: payload.id,
          type: payload.type
        })
      }
    })
    notification.show()
  })

  ipcMain.handle('secure:get', (_event, key) => {
    if (!ALLOWED_SECURE_KEYS.has(key)) return ''
    const raw = readSecureStore()[key]
    if (!raw) return ''
    try {
      if (safeStorage.isEncryptionAvailable()) {
        return safeStorage.decryptString(Buffer.from(raw, 'base64'))
      }
      return Buffer.from(raw, 'base64').toString('utf8')
    } catch {
      return ''
    }
  })

  ipcMain.handle('secure:set', (_event, key, value) => {
    if (!ALLOWED_SECURE_KEYS.has(key)) return false
    const store = readSecureStore()
    if (!value) {
      delete store[key]
    } else {
      const text = String(value)
      store[key] = safeStorage.isEncryptionAvailable()
        ? safeStorage.encryptString(text).toString('base64')
        : Buffer.from(text, 'utf8').toString('base64')
    }
    writeSecureStore(store)
    return true
  })

  ipcMain.handle('secure:remove', (_event, key) => {
    if (!ALLOWED_SECURE_KEYS.has(key)) return false
    const store = readSecureStore()
    delete store[key]
    writeSecureStore(store)
    return true
  })
}

if (gotSingleInstanceLock) {
  app.on('second-instance', () => {
    expandFromBall()
  })

  app.whenReady().then(() => {
    registerIpc()
    if (!isDev) {
      Menu.setApplicationMenu(null)
    }
    // 生产环境也预创建主窗口（隐藏），让通知 SSE 在悬浮球形态下保持在线
    createMainWindow({ showOnReady: isDev })
    createBallWindow()
    if (isDev) {
      hideBall()
    }
  })

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createBallWindow()
    }
  })

  app.on('before-quit', () => {
    isQuitting = true
  })

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
      app.quit()
    }
  })
}
