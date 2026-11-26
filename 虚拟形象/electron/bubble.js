const bubble = document.getElementById('bubble')
const label = document.getElementById('label')
const text = document.getElementById('text')

function show(payload) {
  const labelText =
    payload?.label === undefined ? '到时间了' : String(payload.label || '').trim()
  const taskTitle = String(payload?.taskTitle || '').trim()
  const body = payload?.text
    ? String(payload.text).trim()
    : taskTitle
      ? `该做「${taskTitle}」了`
      : String(payload?.title || payload?.body || '').trim()
  if (!body) return
  label.textContent = labelText
  label.style.display = labelText ? '' : 'none'
  text.textContent = body
  bubble.classList.add('visible')
}

function hide() {
  bubble.classList.remove('visible')
}

window.desktopBridge?.onBallBubble?.((payload) => show(payload))
window.desktopBridge?.onBallBubbleClear?.(() => hide())
