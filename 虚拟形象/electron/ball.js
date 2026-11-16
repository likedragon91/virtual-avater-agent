const bridge = window.desktopBridge
const core = document.getElementById('core')
const ball = document.getElementById('ball')

function expand() {
  if (bridge && typeof bridge.expand === 'function') {
    bridge.expand()
  }
}

if (core) {
  core.addEventListener('click', (event) => {
    event.stopPropagation()
    expand()
  })
}

// 拖拽区在部分 Windows 环境下不派发 click，双击也作为展开入口
if (ball) {
  ball.addEventListener('dblclick', expand)
}
