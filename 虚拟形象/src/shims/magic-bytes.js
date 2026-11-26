// live2d-renderer 通过 import("magic-bytes.js").default 取检测函数；
// 这里优先使用真实实现，并保底提供 zip 魔数检测，确保 zip 模型能加载。
import * as namespace from 'magic-bytes.js/dist/index.js'

function zipFallback(bytes) {
  const array = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)
  const isZip = array[0] === 0x50 && array[1] === 0x4b
  return [{ mime: isZip ? 'application/zip' : '' }]
}

const resolved =
  (typeof namespace.default === 'function' && namespace.default) ||
  (typeof namespace.filetypeinfo === 'function' && namespace.filetypeinfo) ||
  (typeof namespace.default?.filetypeinfo === 'function' && namespace.default.filetypeinfo) ||
  zipFallback

export default resolved
