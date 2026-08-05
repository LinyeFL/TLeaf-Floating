# TLeaf-Floating

讲台悬浮书名插件（Paper 1.21.4+ / Java 21）

把书放进讲台，书名会以悬浮文字显示在讲台上方。放书的玩家本人扔道具即可变色、闪烁、发光、开启炫彩模式。只有创建者能操作，别人扔了无效。

## 功能

- 放书即显示书名，取书/敲掉讲台自动消失
- 扔染料（16 色）：把书名切成对应颜色，稳定显示不闪烁
- 扔骨粉：开关闪烁（当前色 ↔ 白色来回切换）
- 扔荧光墨囊：开关发光效果
- 扔钻石：开关炫彩模式（彩色平滑流动）
- 炫彩与闪烁互斥，发光可叠加
- 只有放书的创建者本人能操作，别人扔道具无效且不消耗

## 环境要求

- Paper 1.21+（26.2 服务端兼容）
- Java 21

## 安装

1. 下载 `TLeaf-Floating-1.0.0.jar`
2. 放入服务器 `plugins` 文件夹
3. 重启服务器
4. 给玩家权限：`lp user <玩家> permission set tleaf-floating.use true`

## 使用

1. 把书（成书，或铁砧改过名的书与笔）放到讲台上，书名悬浮显示
2. 对准讲台扔道具即可操作

| 道具 | 效果 |
| --- | --- |
| 染料（16 色） | 书名变色（稳定） |
| 骨粉 | 闪烁 开/关 |
| 荧光墨囊 | 发光 开/关 |
| 钻石 | 炫彩模式 开/关 |

## 权限

| 权限节点 | 说明 |
| --- | --- |
| `tleaf-floating.use` | 允许触发悬浮显示 |
| `tleaf-floating.reload` | 允许执行 /tfl reload |

## 命令

| 命令 | 说明 |
| --- | --- |
| `/tfl` | 查看帮助 |
| `/tfl reload` | 重载配置（config.yml + messages.yml，免重启） |

## 配置（config.yml）

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| default-color | #FFFFFF | 默认文字颜色 |
| text-scale | 1.0 | 文字缩放倍数 |
| y-offset | 0.6 | 文字在讲台上方高度（格） |
| shadowed | true | 是否显示阴影 |
| blink-interval | 10 | 闪烁间隔（tick，20 tick = 1 秒） |
| ray-distance | 2 | 扔道具检测的射线距离（格） |
| rainbow-speed | 1.0 | 炫彩每秒流转圈数 |
| rainbow-interval | 1 | 炫彩每几 tick 刷新一次颜色 |

消息文案在 `messages.yml`，改完重启生效。

## 构建（开发向）

```bash
mvn package
