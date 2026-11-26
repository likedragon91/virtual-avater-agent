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

/**
 * 让画布适配容器。
 *
 * - 默认按宽度取整数倍，保证像素严格对齐；
 * - fill 模式下同时考虑宽高，用最大可用倍率撑满舞台（允许小数倍），
 *   并保留 image-rendering: pixelated，避免像素边缘发虚。
 */
export function fitStage(
  container: HTMLElement,
  canvas: HTMLCanvasElement,
  {
    fill = false,
    gutter = 0,
    minScale = 1
  }: { fill?: boolean; gutter?: number; minScale?: number } = {}
): number {
  const width = (container.clientWidth || STAGE_SIZE) - gutter
  const height = (container.clientHeight || STAGE_SIZE) - gutter
  const scale = fill
    ? Math.max(minScale, Math.min(width, height) / STAGE_SIZE)
    : Math.max(minScale, Math.floor(width / STAGE_SIZE))

  const rounded = Math.round(scale * 100) / 100
  canvas.style.width = `${STAGE_SIZE * rounded}px`
  canvas.style.height = `${STAGE_SIZE * rounded}px`
  return rounded
}
