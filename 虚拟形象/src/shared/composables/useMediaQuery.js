import { onBeforeUnmount, ref } from 'vue'

/**
 * 订阅媒体查询结果，供布局/视图在 JS 中切换行为。
 * 注意：必须在组件 setup 中调用。
 *
 * @param {string} query 例如 '(max-width: 900px)'
 * @returns {{ matches: import('vue').Ref<boolean> }}
 */
export function useMediaQuery(query) {
  const matches = ref(false)

  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return { matches }
  }

  const mediaQueryList = window.matchMedia(query)
  matches.value = mediaQueryList.matches

  const handleChange = (event) => {
    matches.value = event.matches
  }

  mediaQueryList.addEventListener('change', handleChange)
  onBeforeUnmount(() => mediaQueryList.removeEventListener('change', handleChange))

  return { matches }
}

/** 主断点：≤900px 视为手机/窄屏 */
export function useIsMobile() {
  return useMediaQuery('(max-width: 900px)')
}
