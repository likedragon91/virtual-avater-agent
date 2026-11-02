import { Application, TextureStyle } from 'pixi.js'

/** 逻辑分辨率固定 256×256，所有素材按 1x 像素绘制 */
export const STAGE_SIZE = 256

// 像素风必须使用最近邻采样，避免纹理发虚
TextureStyle.defaultOptions.scaleMode = 'nearest'

/**
 * 创建 Pixi 舞台
 *
 * resolution 固定为 1、关闭 autoDensity，缩放完全交给 CSS 整数倍完成；
 * 用 autoDensity 做小数缩放会让像素糊掉。
 */
export async function createStage(host: HTMLElement): Promise<Application> {
  const app = new Application()

  await app.init({
    width: STAGE_SIZE,
    height: STAGE_SIZE,
    backgroundAlpha: 0,
    antialias: false,
    roundPixels: true,
    resolution: 1,
    autoDensity: false
  })

  const canvas = app.canvas as HTMLCanvasElement
  canvas.style.imageRendering = 'pixelated'
  canvas.style.width = `${STAGE_SIZE}px`
  canvas.style.height = `${STAGE_SIZE}px`
  canvas.style.display = 'block'

  host.appendChild(canvas)
  return app
}

/** 按容器宽度计算整数缩放倍率（最小 1×） */
export function getStageScale(container: HTMLElement): number {
  const width = container.clientWidth || STAGE_SIZE
  return Math.max(1, Math.floor(width / STAGE_SIZE))
}

/** 以 CSS 整数倍放大画布，保持像素锐利 */
export function fitStage(container: HTMLElement, canvas: HTMLCanvasElement): number {
  const scale = getStageScale(container)
  canvas.style.width = `${STAGE_SIZE * scale}px`
  canvas.style.height = `${STAGE_SIZE * scale}px`
  return scale
}
