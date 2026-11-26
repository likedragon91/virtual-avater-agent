# Project AIRI 虚拟形象技术拆解

> 对象：[moeru-ai/airi](https://github.com/moeru-ai/airi)（当前主线 v0.15.x，MIT）
> 目标：搞清楚它「用户自定义形象 + 有动作 + 有声音」这三件事分别是怎么落到代码上的，以及要复刻时的最小路径。
> 说明：下文路径都相对仓库根目录；带「推断」字样的地方是从类型签名/调用关系反推的，仓库里没有直接注释。

---

## 0. 一句话结论

AIRI 把「数字人」拆成了四个可替换的层，层与层之间用 **Pinia store + Web Audio 节点 + 插件链** 解耦：

| 层 | 抽象 | 实现 |
|---|---|---|
| 形象 | `DisplayModel`（8 种格式）→ `AvatarModelReference`（角色卡持有） | Live2D / VRM / MMD / Spine / 立绘，5 套独立渲染包 |
| 动作 | 「参数」而不是「动画文件」 | Live2D Cubism 参数、VRM 表情/骨骼、Spine 轨道、VMD |
| 声音输入 | `StreamingTranscriber` | Silero VAD + Web Speech / sherpaw / OpenAI 兼容 / Apple Speech |
| 声音输出 + 口型 | `SpeechOutput` + wLipSync AudioWorklet | 多 TTS 供应商 → 播放管线 → 口型节点（元音权重） |

关键取舍：**它不只播放预录动画，而是把形象当成一组可实时写入的参数**（`ParamMouthOpenY`、`aa/ih/ou/ee/oh`、`expressionManager.setValue`）。动作文件负责「躯干大动作」，实时参数负责「每帧都在变的细节」，所以口型、眨眼、视线、呼吸才能被 TTS 音频、LLM 情绪、鼠标、麦克风、甚至统计模型同时驱动而互不打架。

---

## 1. 工程分层（monorepo 视角）

`pnpm-workspace.yaml` 的 catalog 已经能看出全部技术选型：

- 渲染：`pixi-live2d-display@0.4`（打了 patch）、`@pixiv/three-vrm@3.5` + `three@0.185` + `@tresjs/core`、`@esotericsoftware/spine-webgl`（4.0/4.1/4.2 三个版本并存）、`@moeru/three-mmd`（+ `three-mmd-physics-ammo`）
- 音频：`wlipsync@1.3`、`fft.js`、`kokoro-js`、`onnxruntime-web`
- 模型/推理：`@huggingface/transformers@3.8`、`@sherpaw/asr`、`@sherpaw/kws`（唤醒词）、`@mediapipe/tasks-vision`
- UI：Vue 3 + Pinia（`pinia-plugin-synced` 做跨窗口同步）+ UnoCSS

与本主题直接相关的包：

```
packages/
  stage-ui/                 # 主 UI + 所有业务 store（settings / speech / voice / display-models …）
  stage-ui-live2d/          # Live2D 渲染 + motion 插件链 + 模型 zip/OPFS 加载
  stage-ui-three/           # three.js 舞台 + VRM
  stage-ui-mmd/             # MMD（PMX/PMD + VMD）
  stage-ui-spine/           # Spine
  stage-ui-tachie/          # 立绘（分层图片差分）
  core-character/           # 角色卡里的 AvatarModelReference 类型
  model-driver-lipsync/     # wLipSync 封装（Live2D / VRM / MMD 共用）
  pipelines-audio/          # 播放管线（Playback / BrowserPlayback）
  motion-driver-magic/      # MAGIC：统计模型生成连续动作
  model-driver-magic-live2d/# MAGIC 的 Live2D 姿态映射与滤波
```

---

## 2. 「用户自定义形象」怎么做的

### 2.1 统一抽象：DisplayModel

`packages/stage-ui/src/stores/display-models.ts`

```ts
export enum DisplayModelFormat {
  Live2dZip = 'live2d-zip',
  Live2dDirectory = 'live2d-directory',
  VRM = 'vrm',
  SpineZip = 'spine-zip',
  TachieZip = 'tachie-zip',
  PMXZip = 'pmx-zip',
  PMXDirectory = 'pmx-directory',
  PMD = 'pmd',
}

export type DisplayModel = DisplayModelFile | DisplayModelURL
```

- `DisplayModelFile`：用户导入的文件（`File` 对象 + `format` + `previewImage` + `importedAt` + 可选 `cloudOwnerId`）
- `DisplayModelURL`：内置预设（Hiyori Live2D 的 free/pro zip、`AvatarSample_A/B.vrm`）
- 内置 4 个预设，用户模型和预设**在同一个列表里**，UI 上无差别可选

这个 store 还负责：缩略图生成（`generatePreview` → 每种格式一个离屏渲染器）、IndexedDB 持久化（`displayModelsRepo`）、云端同步（`display-model-cloud`，仅 Live2D zip 与 VRM 参与同步）、重命名、删除、下载校验（`downloadVerified`）。

**导入接口只有一个**：

```ts
await addDisplayModel(DisplayModelFormat.VRM, file)
// → 生成 id `display-model-<nanoid>` → 生成预览图 → push 到列表 → 写 IndexedDB → 广播其他窗口 → 可选上传
```

### 2.2 选择链路：从 store 到渲染器

`packages/stage-ui/src/stores/settings/stage-model.ts`

```
用户点选模型 (ModelSelectorDialog)
   → stageModelSelected = <displayModelId>
   → updateStageModel()
       → displayModelsStore.ensureDisplayModelAvailable(id)   // 本地没有就向云端下载
       → model.type === 'file' ? URL.createObjectURL(model.file) : model.url
       → resolveBuiltInStageModelRenderer(model.format)       // format → 'live2d' | 'vrm' | 'mmd' | 'spine' | 'tachie'
       → stageModelRenderer = 渲染器
       → stageModelSelectedUrl = blob:/http: URL
```

注意一个细节：**模型身份和模型 URL 是分开的**。`stageModelSelected`（稳定 id）和 `stageModelSelectedUrl`（每次启动都会变的 blob URL）分别存储，`VRMModel.vue` 里专门有 `resetLegacyModelIdentity()` 处理历史遗留（旧版本把 URL 当 id，导致重启后视角配置错位）。做同类产品时这点很容易踩坑。

### 2.3 各渲染器怎么吃这个 URL

| 渲染器 | 入口 | 加载方式 |
|---|---|---|
| Live2D | `stage-ui-live2d/src/components/scenes/live2d/Model.vue` | `Live2DFactory.setupLive2DModel(model, { url, id })` |
| VRM | `stage-ui-three/src/components/Model/VRMModel.vue` | `loadVrm(modelSrc)`（`composables/vrm/core`）+ `@pixiv/three-vrm` |
| MMD | `stage-ui-mmd` | three-stdlib `MMDLoader` + Ammo 物理 |
| Spine | `stage-ui-spine` | `spine-webgl` 运行时 |
| 立绘 | `stage-ui-tachie` | 分层贴图 + 表情差分 |

### 2.4 Live2D 导入的「兜底」是重点

`stage-ui-live2d/src/utils/live2d-zip-loader.ts` 重写了 `pixi-live2d-display` 的 `ZipLoader` / `FileLoader`，处理了真实世界里一堆脏数据：

1. **没有 `.model3.json` 也能开**：`createFakeSettings()` 扫描 zip，找唯一的 `.moc3` → 用同名做模型名，收所有 `.png` 当贴图，收 `.mtn` / `.motion3.json` 当动作，`physics` / `pose` 按文件名猜。
2. **文件名编码**：VTube Studio 导出的 zip 常常没有 UTF-8 flag，`decodeZipFileName` 按 GBK 兜底解码（否则 `手姿势切换.exp3.json` 会变乱码，后续校验直接拒绝）。
3. **settings JSON 清洗**：`"Physics": null` 这类会被上游路径解析炸掉，统一删字段。
4. **路径 decode**：`Model%20Package/Avatar%20Model.moc3` → 真实解压路径。
5. **顺手抽取元数据**：`.cdi3.json`（显示名/作者）与所有 `.exp3.json` 表情文件都缓存进 settings，供后续表情列表 UI 用。
6. **忽略 `__MACOSX` / `._*`**。

### 2.5 OPFS 缓存（第二次打开秒开）

`stage-ui-live2d/src/utils/opfs-loader.ts`：以 `modelId` 为 key，把 zip 解压成 OPFS 目录树，下次 `checkMiddleware` 命中就直接把 `File[]` 塞回 loader，跳过解压。带 `__meta.json` 记录 `sourceUrl` 与 schema 版本（当前 v4），来源 URL 变了就整目录失效重建。

这解决的是：Live2D 模型动辄 10–50 MB、几百个文件，每次启动都解压 + 重新注册 blob URL 会卡到没法用。

### 2.6 角色卡持有「用哪个模型 + 能用哪些动作」

`packages/core-character/src/avatar-model.ts`

```ts
export const avatarModelTypes = ['live2d', 'vrm', 'mmd', 'spine', 'tachie'] as const

export interface AvatarModelReference<TType, TConfig> {
  readonly id: string
  readonly displayModelId: string   // 指向 DisplayModel
  readonly type: TType
  readonly config: TConfig
}
```

Live2D 的 `config` 是 `Live2DControlPolicy`：

```ts
interface Live2DControlPolicy {
  disabledExpressions: string[]  // 运行时拒绝的表情名
  disabledMotions: string[]      // 运行时拒绝的动作文件名
}
```

含义：**用户换的不只是皮，还有「这个皮允许 AI 用哪些动作」**。`Stage.vue` 里 `createLive2D({ controlPolicy: () => selectedAvatarModel.value?.config.controls })` 把它注入渲染上下文，动作/表情执行时被过滤。这是「自定义形象」里最容易被忽略、但产品上很重要的一环（防止 AI 乱用作者的付费动作）。

---

## 3. 「有动作」怎么做的

### 3.1 Live2D：把 Cubism 的 `motionManager.update` 改成插件链

AIRI 没有自己写一套动画系统，而是**劫持** `pixi-live2d-display` 的 `internalModel.motionManager.update`：

`stage-ui-live2d/src/components/scenes/live2d/Model.vue`

```ts
const motionManagerUpdate = useLive2DMotionManagerUpdate({ internalModel, motionManager, ... })

motionManagerUpdate.register(beatSyncPlugins.pre, 'pre')
motionManagerUpdate.register(useMotionUpdatePluginIdleDisable(), 'pre')
motionManagerUpdate.register(useMotionUpdatePluginIdleFocus(), 'post')

motionManagerUpdate.register(useMotionUpdatePluginExpression(live2d.expressions), 'final')
motionManagerUpdate.register(useMotionUpdatePluginAutoEyeBlink(live2dExpressionEnabled), 'final')
motionManagerUpdate.register(useMotionUpdatePluginLightSquint(...), 'final')
motionManagerUpdate.register(useMotionUpdatePluginManualControl(manualMotionControl, manualMotionSpring, ...), 'final')
motionManagerUpdate.register(beatSyncPlugins.layer, 'final')
motionManagerUpdate.register(useMotionUpdatePluginLipSync(mouthOpenSize, nowSpeaking), 'final')
motionManagerUpdate.register(useMotionUpdatePluginBreathControl(manualBreathControl), 'final')
motionManagerUpdate.register(useMotionUpdatePluginTouchReaction(touchReaction.sample), 'final')

const hookedUpdate = motionManager.update
motionManager.update = function (model, now) {
  return motionManagerUpdate.hookUpdate(model, now, hookedUpdate)
}
```

`packages/stage-ui-live2d/src/composables/live2d/motion-manager.ts` 定义了三个阶段：

- **pre**：先跑，能「接管」本帧（`markHandled()`），跑完就不再走 SDK 的曲线
- **post**：SDK 曲线跑完之后
- **final**：**无论是否 handled 都跑**，用来做叠加/覆盖（表情、口型、呼吸、触摸反应）

这个设计非常值得抄：SDK 的动作文件（`.motion3.json`）负责「躯干大动作」，插件负责「每帧都在变的细节」，两者不打架。

具体每个插件在干什么：

| 插件 | 作用 | 实现要点 |
|---|---|---|
| `AutoEyeBlink` | 自动眨眼 | 自己写状态机（idle/closing/opening），闭眼 75 ms，睁眼 150–300 ms 随机，间隔 3–8 s 随机；**乘法调制**而不是覆盖，这样表情闭眼时不会被眨眼顶开 |
| `IdleFocus` / `focus-bias` | 待机眼球扫视 | 随机注视点 + 目光朝鼠标/摄像头偏置 |
| `Breath` | 呼吸 | 半余弦吸气 + 半余弦呼气 + 停留，写 `ParamBreath`；先 `delete internalModel.breath` 把 SDK 自带的呼吸关掉 |
| `BeatSync` | 跟节拍点头 | 弹簧（刚度 120 / 阻尼 16）驱动 `ParamAngleX/Y/Z`，通过 `BroadcastChannel` 接收节拍信号 |
| `TouchReaction` | 摸头/戳身体反应 | 命中检测（hitArea → 头部包围盒 → 逐 drawable 网格），叠加 `ParamAngle*`、`ParamBodyAngle*`、眯眼 |
| `LightSquint` | 屏幕变亮时眯眼 | 快/慢两个跟随器（0.15 s / 4 s 适应亮、90 s 适应暗），只取「变亮的速度」而不是亮度本身 |
| `ManualControl` | 手动/程序化姿态 | 摇杆或 MAGIC 输出 `Pose` → 弹簧平滑 → 写 13 个标准参数 |

`Pose` 的字段就是它抽象出的「通用身体控制面」：

```ts
{ eyeX, eyeY, eyeSquint, headX, headY, headZ, bodyX, bodyY, bodyZ, mouthForm, mouthOpen, offsetX, offsetY }
```

### 3.2 VRM：每帧手动调子系统

`stage-ui-three/src/components/Model/VRMModel.vue` 的 `onBeforeRender` 里，顺序是刻意排的：

```
AnimationMixer.update(delta)        // VRMA 待机动作（.vrma 文件）
材质 update(delta)                   // MToon 的 uniform 必须手动喂
humanoid.update()
lookAt.update()
blink.update()                       // sin(π·progress)，0.2 s 一次，间隔 1–6 s
lipSync.update()                     // 写 aa/ih/ou/ee/oh
emote.update({ skipVisemes })        // 表情状态机，说话时让出口型
expressionManager.update()
nodeConstraintManager.update()
springBoneManager.update(delta)      // 头发/裙子物理
```

注释里写明了顺序原因：口型先于 emote，且 emote 在 `isLipSyncActive` 时 `skipVisemes`，否则第一帧口型会被表情覆盖；emote 结束后再把嘴型还回去。

待机动作是 `@pixiv/three-vrm-animation` 的 **VRMA**（`animations.idleLoop`），并且有个 `reAnchorRootPositionTrack()`：把动画里 hips 的位置轨道平移到模型当前 hips 位置，否则每个模型都会「飘」。

### 3.3 情绪/动作信号从哪来

LLM 的流式输出里带特殊 token，聊天层解析成 `act` / `delay` 信号，`Stage.vue` 里统一分发：

```ts
streamingControl.onSignal(async (signal) => {
  if (signal.type === 'act') {
    const act = normalizeActPayload(signal.payload)
    if (act.motion)    await live2d.motions.execute(act.motion)          // Live2D 指定动作文件名
    if (act.expression === null) await live2d.expressions.resetExecution()
    else if (act.expression)     await live2d.expressions.execute(act.expression)
    if (act.emotion)   emotionsQueue.enqueue(toStageEmotionPayload(act.emotion))
  }
  if (signal.type === 'delay') await sleep(signal.seconds * 1000)        // 让「动作时序」跟着说话节奏
})
```

`emotionsQueue` 再按渲染器分派（`packages/stage-ui/src/constants/emotions.ts` 是映射表）：

| 情绪 | Live2D motion group | VRM 表情 | Spine 动画 |
|---|---|---|---|
| happy | `Happy` | `happy` | `celebrate` |
| sad | `Sad` | `sad` | `sad` |
| angry | `Angry` | `angry` | `angry` |
| think | `Think` | `think` | `think` |
| surprised | `Surprise` | `surprised` | `surprise` |
| awkward | `Awkward` | `neutral` | `awkward` |
| question | `Question` | `think` | `question` |
| curious | `Curious` | `think` | `curious` |
| neutral | `Idle` | `neutral` | `idle` |

`delay` 这个设计挺巧：模型可以在句子里插 `[delay:0.8]`，让「说完这句再笑」这种表演时序成立。

### 3.4 MAGIC：用统计模型「无限生成」动作

这是 AIRI 里最特别的一块，也是它区别于「几个 Idle 循环」的方案。

`stage-ui/src/features/motions/live2d/use-live2d-motion-magic.ts` + `packages/motion-driver-magic`：

- 输入是**录好的姿态数据集**（`Live2DMotionMagicDataset`：一串 `{ atMs, ...Pose }` 采样）
- 用两种方法拟合：**VAR**（向量自回归，`order` 默认 20 + ridge 0.001）或 **AR-HMM**（5 个隐状态 × 12 阶）
- 拟合完 `toGenerator({ seed })` 就能**无限采样**出连续的姿态序列，再经输出滤波（限速、平滑）写回 `Pose`
- 有两套 profile：`idle` 和 `speaking`，靠 `useLive2DMotionSpeechActivity()` 在说话时 1.6 s 混合过去、说完 3 s 混合回来
- 生成的动作里默认 `skipMouthOpen`，避免和口型插件抢 `ParamMouthOpenY`

也就是说：**待机不再是循环播放，而是一个在「人类动作分布」上采样的生成器**。这解决了循环动作能被一眼看穿的问题。

另外还有个 `model-driver-mediapipe`（MediaPipe 人脸/姿态 → 驱动模型），说明它同一套 `Pose` 抽象既接「生成」也接「捕捉」。

---

## 4. 「有声音」怎么做的

### 4.1 输入：VAD → 流式 ASR

`stage-ui/src/stores/voice.ts` + `stores/modules/hearing.ts`

```
麦克风 (settings/audio-device)
  → SileroVad（workers/vad/silero-vad，ONNX）
  → 语音活动判定 / 唤醒词（@sherpaw/kws）
  → hearing.createTranscriber()（流式）
  → 文本 → 聊天 store → LLM
```

三种输入模式（`HearingInputMode`）：

- `always-on`：VAD 检测到说话就开一次输入
- `push-to-talk`：按住空格才开麦
- `wake-word`：只有喊唤醒词才开输入

细节：`VOICE_END_SILENCE = { pauseMs: 1200, graceMs: 3000 }`——静音 1.2 s 结束，但如果开了「结束判定模型」，会多给 3 s 让用户想词；还有个 `useVoiceEndJudge` 用聊天模型判断「这句话说完了没」。`acceptSpeech` 里做了 barge-in（AI 说话时用户插话），条件是系统回声消除开启或外部分类器判定。

ASR 后端可插拔（`STREAM_TRANSCRIPTION_EXECUTORS`）：Web Speech API、sherpaw（本地）、Apple Speech（原生插件）、阿里云 NLS、任意 OpenAI 兼容 `/audio/transcriptions`。走 PCM 还是整段 WAV 由 provider 的 `features.supportsStreamInput` 决定。

### 4.2 输出：流式 TTS → 播放管线

`stage-ui/src/stores/modules/speech.ts` + `pipelines-audio`

```
LLM token 流
  → onTokenLiteral → speechStream.write(literal)     // core-agent 按句切块
  → speechStore.createOutput(turn, card.modules.speech, ...) → { synthesize(text) }
  → generateSpeech({ model, voice, input })           // @xsai/generate-speech，或双向 WS 流式
  → AudioBuffer → Playback(BrowserPlayback(audioContext, { destination: speechDestination }))
  → GainNode(音量) → audioContext.destination
```

- 每个**角色卡**自带 `modules.speech = { provider, model, voice_id }`，`Stage.vue` 里 `cards.getModules(characterId).speech` 取用 → 不同角色不同嗓音
- 供应商：ElevenLabs、Microsoft/Azure Speech、OpenAI 兼容、阿里云百炼（cosyvoice-v2，支持 SSML）、AIRI 官方 `/audio/speech`、本地 Kokoro；还有 `transport: 'bidirectional-ws'` 的双向流式 TTS（`streamSpeech`，资源如 `seed-tts-2.0`）
- 情绪/语速：`pitch` / `rate` / `ssmlEnabled`，`generateSSML()` 用 xast 拼 `<speak><voice><prosody>`

### 4.3 口型：一条音频，三个渲染器

**共同的音频节点**：`@proj-airi/model-driver-lipsync` 包了 [`wlipsync`](https://github.com/mrxz/wlipsync)（MIT，作者 Noeri Huisman；基于 uLipSync 的 **MFCC + WASM** 实现，跑在 AudioWorklet 里）。

为什么是 MFCC 而不是「FFT 能量」：能量只能告诉你「现在有声音/多大声」，MFCC 能把共振峰分布映射到元音上，于是 `A/E/I/O/U` 五个权重会随发音变化——嘴形才对得上字。

Live2D 路径（`model-driver-lipsync/src/live2d/index.ts`）：

```ts
const node = await createWLipSyncNode(audioContext, profile)
// wlipsync 输出 A/E/I/O/U/S 六个权重 + 一个 volume
// S（摩擦音）映射成 I，避免嘴巴硬切到 0
const amp = min(node.volume * 0.9, 1) ** 0.7
projected[vowel] = max(projected[vowel], min(0.7, raw * amp))
getMouthOpen() = max(...vowel 权重)     // 上限 0.7
// 库默认 40 ms 更新一次 + 120 ms lerp；Stage.vue 里覆盖成 50 ms / 50 ms，更跟手
```

然后在 `Stage.vue` 里每帧读：

```ts
const tick = () => {
  mouthOpenSize.value = nowSpeaking.value ? live2dLipSync.value.getMouthOpen() : 0
  requestAnimationFrame(tick)
}
```

`mouthOpenSize` 一路传到 `Live2DModel` 的 `useMotionUpdatePluginLipSync(mouthOpenSize, nowSpeaking)`，最终写 `ParamMouthOpenY`。

**收尾逻辑是这个插件最精细的地方**（`motion-manager.ts`）：

- 说话中：直接写值，同时把 `releaseRemainingMs = 200`、`handoffRemainingMs = 500` 续上
- 说完后 200 ms：用 smoothstep 从「最后的口型值」混合回 motion 驱动的值
- 再 500 ms：**强制 `ParamMouthOpenY = 0`**，之后才交还给 motion/expression
- 为什么：很多模型的 Idle 动作曲线里 `ParamMouthOpenY` 不是 0，直接交还会让嘴「半张着不闭」

VRM 路径（`stage-ui-three/src/composables/vrm/lip-sync.ts`）：

```ts
const VRM_EXPRESSION_BY_VOWEL = { A: 'aa', E: 'ee', I: 'ih', O: 'oh', U: 'ou' }
// 每个元音一个权重，写进 expressionManager.setValue(...)
// 有任一权重 > 0.01 → isLipSyncActive = true，emote 本帧 skipVisemes
```

注意这是 **5 个元音口型**而不是单纯开合，所以 VRM 的嘴型比 Live2D 的单参数更自然。MMD 走同一套 `createWLipSyncVowelDriver`。

**信号从哪接**：`Stage.vue`

```ts
const speechDestination = audioContext.createGain()          // 音量
const connectSpeech = (node) => voice.connectSpeechNode(node) // 关键：作为 output plugin 挂到播放链

// 音量在口型节点之后
speechDestination.gain.setTargetAtTime(speechVolume, ...)
```

`voice.connectSpeechNode()` 把节点注册成 `controller.use({ setup(scope) { scope.output.connect?.(node) } })` 的插件，于是：

- 音量滑到 0，嘴巴照样动（口型拿到的是**音量前**的信号）
- 换了一路播放（`replacePlayback`）也不影响已挂载的节点
- 同一个口型节点同时挂给「频谱分析器」和「口型」用

### 4.4 一次完整对话的时序

```
        麦克风                                             扬声器
          │                                                  ▲
      SileroVad                                         GainNode
          │                                                  ▲
   ASR（流式，出增量文本）                              BrowserPlayback
          │                                                  ▲
          └──→ 聊天 store ──→ LLM ──token──→ SpeechStream ──┘
                                   │            │ 按句切块
                                   │            └→ TTS（ElevenLabs/…/本地 Kokoro）
                                   │
                                   └→ 特殊 token 解析
                                        ├─ act.motion → live2d.motions.execute()
                                        ├─ act.expression → live2d.expressions.execute()
                                        ├─ act.emotion → 队列 → motion group / VRM 表情
                                        └─ delay → sleep()

   wLipSync AudioWorklet ←── 接在同一条播放链上
          │
          ├→ Live2D: mouthOpenSize → ParamMouthOpenY（final 插件）
          ├→ VRM:   aa/ih/ou/ee/oh → expressionManager
          └→ MMD:   同一套元音驱动
```

---

## 5. 多窗口这件事（容易被忽略）

AIRI 支持「主舞台一个窗口 + 设置页预览一个窗口 + 桌面宠物一个窗口」同时显示同一个角色，所以所有状态都要跨窗口：

- `pinia-plugin-synced`：store 级同步（`speech`、`hearing`、`settings-stage-model-selection` 都用 `synced: { state: true, actions: [...] }`），并有 leader 选举避免并发写
- `BroadcastChannel`：高频瞬时数据
  - `airi-stores-stage-ui-live2d-motion-control`：手动/MAGIC 姿态（带 ownerId，避免两个窗口互相覆盖；还支持 `claimExclusiveControl` 给 devtools 独占）
  - `airi-stores-stage-ui-three-vrm`：VRM 视角需要重算
  - `airi-caption-overlay` / `airi-chat-present`：字幕与演示

做同类产品时，如果只考虑单窗口，这部分可以完全跳过；但一旦要做「桌面宠物 + 设置页预览」，`ownerId + 独占 + 释放」这套语义是必须的。

---

## 6. 要复刻的话：最小可行栈

### 6.1 三步走

**第一步：只做「能换模型 + 会眨眼 + 会说话张嘴」**

1. `pixi-live2d-display@0.4`（或官方 Cubism Web SDK）+ 一个 Live2D zip
2. 重写 `ZipLoader`：没有 `model3.json` 时扫 `.moc3` 造 settings（这是能用「网上下载的模型」的关键）
3. `requestAnimationFrame` 里读音频能量 → 写 `ParamMouthOpenY`
4. 眨眼：自己写状态机，别指望模型自带（很多模型只有 `Idle` 里一条眨眼曲线，且不可配）

**第二步：加「真的口型」和「AI 动作」**

1. 换 `wlipsync`（AudioWorklet 里做共振峰/元音分类，比 RMS 好一个量级；纯能量驱动看起来像嚼东西）
2. LLM 输出结构化的 `act`（motion/emotion/expression/delay），不要让它自由发挥表情名
3. 把 `motionManager.update` 包成 pre/post/final 插件链——这是把「SDK 动画」和「实时参数」解耦的最省事办法

**第三步：加 VRM 或第二套渲染器**

1. `three-vrm` + `@tresjs/core`，每帧顺序照抄 `VRMModel.vue`
2. 待机用 `.vrma`，别忘了 `reAnchorRootPositionTrack`
3. 口型用 5 元音（`aa/ih/ou/ee/oh`），和 Live2D 共用同一个 wlipsync 节点

### 6.2 关键取舍

| 问题 | AIRI 的选择 | 我的建议 |
|---|---|---|
| 动作怎么来 | 动作文件（motion3.json）+ 实时参数插件 + 可选 MAGIC 生成 | 先文件+插件，MAGIC 属于加分项 |
| 口型精度 | 元音权重（Live2D 压成一个开合值） | 想做「念得准」就必须上元音/viseme，光看能量不行 |
| 模型存储 | IndexedDB（文件）+ OPFS（解压后目录） | 只做一种格式可以省掉 OPFS |
| 状态管理 | Pinia + 跨窗口 synced | 单窗口直接用 store 就行 |
| 换角色 | DisplayModel + AvatarModelReference 两层 | 别把「模型文件」和「角色人设」混在一个对象里 |

### 6.3 坑

1. **Live2D 参数名不统一**：`ParamMouthOpenY` 是 Cubism 3/4 标准名，但老模型（Cubism 2）是 `PARAM_MOUTH_OPEN_Y`，`pixi-live2d-display` 会做 id 映射，自己写就要处理两套。
2. **授权**：Cubism SDK 有自己的 License（年营收门槛），Live2D 模型也分「可商用/不可商用/禁止改配」，AIRI 只内置了 Hiyori（官方免费样例）和 VRM 的 AvatarSample。做产品必须先理清这条。
3. **中文口型**：wlipsync 的元音分类是给英语/日语设计的，中文韵母（尤其是 `ü`、`er`、复韵母）会退化，实际表现是「嘴动但不太对」。想更好只能自己训 viseme 分类器或按拼音韵母映射。
4. **TTS 延迟**：流式 TTS（`bidirectional-ws`）+ 按句切块是唯一能把首字延迟压到 1 s 内的组合；HTTP 一次性合成必然有 2–3 s 空窗。
5. **VRM 的 `blink` / `aa` 是预设表情名**，模型作者删了就没有，读取前要 `expressionManager.getExpression()` 判空。
6. **省电**：`maxFps`、`renderScale` 都是可配的（`settings/live2d/max-fps`），移动端不限制会烫。

---

## 7. 文件索引（按主题）

| 想看的 | 文件 |
|---|---|
| 模型格式与导入 | `packages/stage-ui/src/stores/display-models.ts` |
| 选择 → URL → 渲染器 | `packages/stage-ui/src/stores/settings/stage-model.ts` |
| 角色卡持有形象 | `packages/core-character/src/avatar-model.ts`、`packages/stage-ui-live2d/src/types/avatar-model.ts` |
| Live2D zip 兜底 / OPFS | `packages/stage-ui-live2d/src/utils/live2d-zip-loader.ts`、`.../opfs-loader.ts` |
| Live2D 舞台 | `packages/stage-ui-live2d/src/components/scenes/Live2D.vue`、`.../live2d/Model.vue`、`.../live2d/Canvas.vue` |
| Live2D 插件链 | `packages/stage-ui-live2d/src/composables/live2d/motion-manager.ts` |
| 手动姿态 / 摇杆 | `packages/stage-ui-live2d/src/stores/motion-control.ts` |
| VRM 舞台 | `packages/stage-ui-three/src/components/Model/VRMModel.vue` |
| VRM 眨眼/扫视/VRMA | `packages/stage-ui-three/src/composables/vrm/animation.ts` |
| VRM 口型 | `packages/stage-ui-three/src/composables/vrm/lip-sync.ts` |
| Live2D 口型封装 | `packages/model-driver-lipsync/src/live2d/index.ts` |
| 总编排（口型/情绪/触摸/播放） | `packages/stage-ui/src/components/scenes/Stage.vue` |
| 情绪映射 | `packages/stage-ui/src/constants/emotions.ts` |
| TTS | `packages/stage-ui/src/stores/modules/speech.ts` |
| ASR | `packages/stage-ui/src/stores/modules/hearing.ts` |
| 语音会话/打断/输入模式 | `packages/stage-ui/src/stores/voice.ts` |
| MAGIC 动作生成 | `packages/stage-ui/src/features/motions/live2d/use-live2d-motion-magic.ts` |

---

*本文基于 `main` 分支（提交 `3fbe807` 附近）源码静态阅读整理。AIRI 迭代很快（v0.15.x），具体文件位置可能变动，但上面这些抽象（DisplayModel / AvatarModelReference / Pose / 插件链 / wlipsync 节点）是稳定的设计骨架。*
