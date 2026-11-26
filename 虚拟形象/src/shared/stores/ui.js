import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 桌面端窗口形态。panelMode=true 时主窗口停靠在角色左侧，
 * 隐藏头部与侧栏，只保留当前会话。
 */
export const useUiStore = defineStore('ui', () => {
  const panelMode = ref(false)

  function setPanelMode(value) {
    panelMode.value = Boolean(value)
  }

  return {
    panelMode,
    setPanelMode
  }
})
