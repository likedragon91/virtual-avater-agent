/**
 * pre / post / final 三阶段插件链（简化自 AIRI）。
 *
 * 当前像素驱动没有 SDK 自带动画，pre/post 主要留给未来 Live2D/VRM；
 * final 阶段的插件在每帧最后叠加表情、口型、呼吸、触摸反应。
 */
export const PLUGIN_PHASES = Object.freeze(['pre', 'post', 'final'])

export function createPluginPipeline() {
  const plugins = []

  return {
    register(plugin, phase = 'final') {
      if (!plugin || typeof plugin.apply !== 'function') {
        throw new Error('plugin 必须提供 apply(ctx)')
      }
      plugins.push({ plugin, phase: PLUGIN_PHASES.includes(phase) ? phase : 'final' })
      return () => {
        const index = plugins.findIndex((item) => item.plugin === plugin)
        if (index >= 0) plugins.splice(index, 1)
      }
    },

    run(ctx) {
      for (const phase of PLUGIN_PHASES) {
        for (const entry of plugins) {
          if (entry.phase !== phase) continue
          entry.plugin.apply(ctx)
        }
      }
    },

    clear() {
      plugins.length = 0
    }
  }
}
