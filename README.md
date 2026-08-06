# TLeaf-Floating 悬浮标题

讲台悬浮书名插件。在讲台上放一本书，翻一页，书名就以悬浮文字点亮在讲台上方。扔染料变色、扔骨粉闪烁、扔荧光墨囊发光、扔钻石炫彩，做会呼吸的标题。

- 环境：Paper 1.21+（api-version 1.21）
- 纯内存运行，无需数据库
- 中文文案，支持 § 颜色码

## 特性

- **翻页点亮**：放书不触发，放书后翻一页书名飘字
- **创建者专属**：只有放书的玩家能操作，别人扔无效
- **效果互斥**：发光 / 闪烁 / 炫彩三者互斥，开一个自动停另外两个；扔染料回稳定色全停
- **持久显示**：走远回来标题不消失，重启服务器后依然在
- **数量上限**：每个玩家默认最多 3 个悬浮标题，防刷屏卡服

## 交互道具

| 道具 | 效果 |
| --- | --- |
| 染料（16 色） | 稳定变色，同时停止闪烁 / 炫彩 / 发光 |
| 骨粉 | 开关渐变闪烁（当前色 ↔ 白 正弦平滑过渡） |
| 荧光墨囊 | 开关发光（多层光晕 + 呼吸灯脉动） |
| 钻石 | 开关炫彩（色环平滑流动的彩虹渐变） |

以上操作仅创建者有效。成功触发即消耗道具；被拒绝（非创建者）或没对准讲台不消耗。

## 快速上手

1. 把 `TLeaf-Floating-1.0.0.jar` 丢进 `plugins`，重启服务器
2. 给玩家权限 `tleaf-floating.use`
3. 玩家把书（签名成书或改名过的书）放到讲台，翻一页 → 悬浮文字点亮
4. 对准讲台扔染料 / 骨粉 / 荧光墨囊 / 钻石 → 切换效果

## 权限

| 权限 | 说明 | 默认 |
| --- | --- | --- |
| `tleaf-floating.use` | 使用讲台悬浮功能 | op |
| `tleaf-floating.reload` | 重载插件配置 | op |
| `tleaf-floating.limit.<数字>` | 该玩家悬浮标题数量上限（最大 1000，多个取最大值） | false |
| `tleaf-floating.bypass` | 无视数量上限 | op |

没有 `limit.<数字>` 权限的玩家走 config 的 `default-limit` 默认值。数量上限只在真正新建悬浮字时检查，染色、翻页改书名不受影响；拆掉讲台计数自动下降。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/tfl help` | 帮助 |
| `/tfl reload` | 重载配置与消息（需 `tleaf-floating.reload`） |

别名：`/tleaf-floating`

## 配置（config.yml）

```yaml
# TLeaf-Floating 讲台悬浮书名插件配置

# 默认文字颜色（无染料时的颜色），白色
default-color: '#FFFFFF'

# 文字大小（缩放倍数，1.0 为默认）
text-scale: 1.0

# 文字在讲台上方的高度（格）
y-offset: 0.3

# 是否显示阴影
shadowed: true

# 渐变闪烁：一次完整渐变（当前色 → 白 → 当前色）的秒数，1.0 = 1 秒一轮
blink-period: 1.0

# 渐变闪烁：每多少 tick 刷新一次颜色（2 = 50ms 一帧，最顺滑）
blink-interval: 2

# 炫彩模式：色环每秒转几圈（1.0 = 一秒转完一整圈彩虹）
rainbow-speed: 1.0

# 炫彩模式：每多少个 tick 刷新一次颜色
rainbow-interval: 2

# 发光模式：光晕层缩放倍数（从内到外，相对文字缩放，1.0 = 与主字一样大）
# 写几个数字就是几层，默认 3 层
glow-scales: [1.05, 1.10, 1.16]

# 发光模式：光晕层文字不透明度（0-255，主字固定 250）
glow-opacity: 150

# 发光模式：光晕颜色混合白色比例（0-1，0.25 = 混 25% 白，更有霓虹感）
glow-white-mix: 0.25

# 呼吸灯：开启后发光时亮度平滑脉动（亮→暗→亮）
breathing-enabled: true

# 呼吸灯：一次完整脉动的秒数（3.0 = 3 秒一轮亮暗）
breathing-period: 3.0

# 呼吸灯：脉动时最暗/最亮的不透明度（0-255）
breathing-min-opacity: 60
breathing-max-opacity: 210

# 呼吸灯：每多少 tick 刷新一次
breathing-interval: 3

# 扔物品检测的射线距离（格，玩家准星对着讲台扔物品）
ray-distance: 2

# 每个玩家默认最多能创建的悬浮标题数量（无 tleaf-floating.limit.<数字> 权限时生效）
default-limit: 3
```

## 消息（messages.yml）

```yaml
# TLeaf-Floating 消息文案（改完重启生效，支持 § 颜色码）
# 值留空（''）表示该操作不提示

# —— 悬浮显示 ——
display-created: '§a悬浮文字已开启'
display-denied: '§c你没有权限使用悬浮功能'
display-removed: ''

# —— 扔染料（创建者专属） ——
color-success: '§a书名颜色已切换'
color-denied: '§c只有这本书的创建者才能染色'

# —— 扔骨粉（创建者专属） ——
blink-on: '§a闪烁已开启'
blink-off: '§a闪烁已关闭'

# —— 扔荧光墨囊（创建者专属） ——
glow-on: '§a发光已开启'
glow-off: '§a发光已关闭'

# —— 扔钻石（创建者专属） ——
rainbow-on: '§a炫彩模式已开启'
rainbow-off: '§a炫彩模式已关闭'

# —— 通用拒绝（骨粉/墨囊/钻石非创建者） ——
denied: '§c只有这本书的创建者才能操作'

# —— 数量上限 ——
limit-reached: '§c你已达到悬浮标题数量上限（{limit} 个）'

# —— 命令 ——
reload-success: '§a配置已重载'
reload-denied: '§c你没有权限执行此命令'
```

## 构建

JDK 21 + Maven：

```bash
mvn package
```

产物：`target/TLeaf-Floating-1.0.0.jar`。仓库已配 GitHub Actions，push 到 main 自动构建并产出 Artifact。

## 协议

[AGPL-3.0](LICENSE)
