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
const isDev = process.argv.includes('--dev') || !app.isPackaged
const SECURE_STORE_FILE = 'secure-store.json'
const ALLOWED_SECURE_KEYS = new Set(['refreshToken'])

let ballWindow = null
let mainWindow = null
let isQuitting = false

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
  if (!ballWindow) return
  const { workArea } = screen.getPrimaryDisplay()
  const width = 72
  const height = 72
  const margin = 24
  ballWindow.setPosition(
    Math.round(workArea.x + workArea.width - width - margin),
    Math.round(workArea.y + workArea.height - height - margin)
  )
}

function createBallWindow() {
  if (ballWindow && !ballWindow.isDestroyed()) {
    ballWindow.show()
    return ballWindow
  }

  ballWindow = new BrowserWindow({
    width: 72,
    height: 72,
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
      sandbox: true
    }
  })

  ballWindow.setAlwaysOnTop(true, 'floating')
  positionBallWindow()
  hardenWindow(ballWindow)

  // 悬浮球没有关闭按钮，右键菜单提供展开与退出
  ballWindow.webContents.on('context-menu', () => {
    Menu.buildFromTemplate([
      { label: '展开虚拟形象', click: expandFromBall },
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

  ballWindow.loadFile(path.join(__dirname, 'ball.html'))
  ballWindow.on('closed', () => {
    ballWindow = null
  })
  return ballWindow
}

function hideBall() {
  if (ballWindow && !ballWindow.isDestroyed()) {
    ballWindow.hide()
  }
}

function showBall() {
  if (!ballWindow || ballWindow.isDestroyed()) {
    createBallWindow()
  } else {
    positionBallWindow()
    ballWindow.show()
  }
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
    mainWindow.hide()
    showBall()
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
  hideBall()
  createMainWindow()
}

function collapseToBall() {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.hide()
  }
  showBall()
}

function registerIpc() {
  ipcMain.on('window:expand', expandFromBall)
  ipcMain.on('window:collapse', collapseToBall)

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
