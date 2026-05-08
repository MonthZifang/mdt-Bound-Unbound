<div align="center">
  <a href="https://github.com/MonthZifang/YUEYUEDAO-TECH">
    <img src="./md/logo.png" alt="YUEYUEDAO TECH Logo" width="720" />
  </a>

  <p><strong>YUEYUEDAO TECH 维护 MDT 绑定与未绑定</strong></p>

  <p>
    <a href="https://github.com/MonthZifang/YUEYUEDAO-TECH"><strong>查看月月岛科技详情</strong></a>
  </p>
</div>

# MDT 绑定与未绑定

基于 com id 和外部接口结果识别玩家是否绑定，并在玩家名称前显示已绑定或未绑定状态，结果写入列表数据系统供其他插件复用。

## 市场固定识别文件

仓库根目录固定提供以下文件，供插件市场识别：

```text
market.plugin.json
plugin.json
```

## 依赖

- `mdt-jump-plugin`
- `mdt-list-data-system`
- 可选依赖：`mdt-api-interface-call`

## 配置文件

首次启动后建议维护以下配置文件：

```text
config/mods/config/mdt-bound-unbound/bound-unbound.properties
```

- 支持控制是否在玩家名称前显示绑定状态。
- 支持分别设置已绑定和未绑定的文本与颜色。
- 支持把绑定结果写入列表数据库中的 `com id` 对象。
- 支持玩家进入时自动检查，也支持其他插件主动触发检查。

## 功能说明

- 通过 jump 插件生成的 com id 关联玩家绑定状态。
- 可通过第三方平台接口、外部 API 或其他插件返回结果。
- 支持在玩家名之前显示已绑定或未绑定状态。
- 绑定结果统一写入列表数据系统，供经济、等级、排行榜等插件复用。

## 数据与写入说明

- 建议列表名使用 `player_bind`。
- 建议字段包含 `bound`、`bindSource`、`bindTime`、`comid`。
- 检测结果应以 com id 为主键，避免玩家改名造成数据错位。

## 命令

- `bind-state-check <uuid>`：按 UUID 主动检查绑定状态。
- `bind-state-set <comid> <true|false>`：手动设置某个 com id 的绑定状态。
- `bind-state-reload`：重新加载绑定识别配置。
- `/bindstate`：查看自己的绑定识别状态。

## Help 注册备注

- `help mdt-bound-unbound`：查看 MDT 绑定与未绑定 的独立命令说明。
- 中文备注建议写为“绑定检查、绑定状态手改、显示配置重载”。

## 插件入口

```text
com.mdt.bound.BoundUnboundPlugin
```

## 版本规则

- 当前插件版本：`v1`
- 当前需求市场版本：`v1`
