const bubble = document.getElementById('bubble')
const textEl = document.getElementById('text')

function render(payload) {
  const text = String(payload?.text || '').trim()
  const error = String(payload?.error || '').trim()
  const pending = Boolean(payload?.pending)

  if (!text && !error && !pending) {
    bubble.hidden = true
    return
  }

  bubble.hidden = false
  textEl.textContent = text || (error ? `回复失败：${error}` : '正在回复…')
  bubble.classList.toggle('is-pending', !text && !error && pending)
  bubble.classList.toggle('is-error', Boolean(error))

  // 流式期间跟随最新内容；结束后回到开头方便阅读完整回复
  requestAnimationFrame(() => {
    textEl.scrollTop = pending ? textEl.scrollHeight : 0
  })
}

window.desktopBridge?.onChatReply?.((payload) => render(payload))
