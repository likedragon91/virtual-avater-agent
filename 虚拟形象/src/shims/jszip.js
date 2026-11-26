// live2d-renderer 通过 import("jszip").default 取构造函数；
// Vite 对 CJS 的动态 import 可能只给命名导出，这里用静态 import 修正 default。
import * as namespace from 'jszip/dist/jszip.min.js'

const resolved = namespace.default || namespace.JSZip || namespace
export default resolved
