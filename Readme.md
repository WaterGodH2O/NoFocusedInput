# NoFocusInput

通过无障碍服务读取当前界面控件树，找出支持 `ACTION_SET_TEXT` 的输入框，再用 ADB 广播写入文本。可按控件 `id` 或屏幕坐标指定目标。写入时不抢设备焦点、不唤起输入法。

## 使用前

1. 安装并打开本 App。
2. 在系统设置中开启无障碍服务 **无焦点输入**（App 内可点「打开无障碍设置」）。
3. 目标输入框需出现在屏幕或任何扩展屏幕上（可无焦点）。

查看相关日志：

```bash
adb logcat -s NodeDump:I SetText:I DemoBroadcast:I
```

## 扫描可写入控件

遍历当前界面（含多显示器窗口），只把**可 `SET_TEXT`** 的节点打到 Logcat，tag 为 `NodeDump`。

```bash
adb shell "am broadcast -n com.example.nofocusinput/.DemoBroadcastReceiver -a com.example.nofocusinput.ACTION_DUMP_NODES"
```

日志里可直接看到 `id`、`class`、`text`、`bounds`、`focused`、窗口上的 `displayId` 等。按 id 写入时填 `--es id`；按坐标写入时从 `bounds` 里取一点作为 `--ei x` / `--ei y`，需要指定屏幕时再带 `--ei displayId`。

## 向控件写入文本

`--es id` 支持完整资源名或短名，例如华为日历标题框：

| 写法 | 示例 |
|------|------|
| 完整 id | `com.huawei.calendar:id/title` |
| 短名 | `title` |

```bash
adb shell "am broadcast -n com.example.nofocusinput/.DemoBroadcastReceiver -a com.example.nofocusinput.ACTION_SET_TEXT --es id 'title' --es text 'hello world'"
```

短名可能命中多个同名控件，会全部写入。需要精确匹配时用完整 id。

## 按坐标写入文本

`--ei x` / `--ei y` 是**某一块屏自己的**像素，与 dump 日志里的 `bounds=[left, top][right, bottom]` 同一套坐标系（`getBoundsInScreen`）。点需要落在目标输入框矩形内。每块屏的左上角都是 `(0, 0)`。

`--ei displayId` 指定要扫哪一块屏，和 dump 日志里的 `displayId` 相同。可省略；省略或空字符串时默认主屏（`0`）。`--es displayId '2'` 也可以。

命中规则：只在指定屏上，按窗口 Z 序从前到后，在包含该点的可 `SET_TEXT` 节点里取面积最小的一个再写入。优先可见节点；没有资源 id 的输入框也能写。指定的屏上没有窗口时，`SetText` 日志会打出 `available=[...]`，里面是当前能扫到的屏幕 id。

主屏（可省略 `displayId`）：

```bash
adb shell "am broadcast -n com.example.nofocusinput/.DemoBroadcastReceiver -a com.example.nofocusinput.ACTION_SET_TEXT_BY_POINT --ei x 540 --ei y 800 --es text 'hello world'"
```

指定屏（例如 scrcpy `--new-display` 打出来的 `displayId`）：

```bash
adb shell "am broadcast -n com.example.nofocusinput/.DemoBroadcastReceiver -a com.example.nofocusinput.ACTION_SET_TEXT_BY_POINT --ei displayId 2 --ei x 540 --ei y 800 --es text 'hello world'"
```

坐标也可写成字符串：`--es x '540' --es y '800'`。

## 查询 / 切换软键盘隐藏

和 Toggle 用同一份无障碍 `SHOW_MODE`：`HIDDEN` 为已隐藏，其它为未隐藏。查询只读，不会改状态。

```bash
adb shell am broadcast -n com.example.nofocusinput/.DemoBroadcastReceiver -a com.example.nofocusinput.ACTION_QUERY_IME_HIDDEN
```

ADB 标准输出：

```text
Broadcast completed: result=-1, data="true"
Broadcast completed: result=-1, data="false"
```

| `data` | 含义 |
|--------|------|
| `"true"` | 输入法已隐藏（`SHOW_MODE_HIDDEN`） |
| `"false"` | 未隐藏 |

必须是小写 `true` / `false`，不是 `True`、`1` 或 JSON。PC 侧解析 `data=` 后面的值即可。无障碍服务未连接时，返回内存里上次的状态。

翻转隐藏（不返回上述 result data）：

```bash
adb shell "am broadcast -n com.example.nofocusinput/.DemoBroadcastReceiver -a com.example.nofocusinput.ACTION_TOGGLE_IME_HIDDEN"
```

## 命令注意事项

- 整条 `adb shell "..."` 用**双引号**包住；`--es` 的值用**单引号**，否则空格会被 shell 拆开。
- 含空格的文本示例：`--es text 'hello world'`
- 广播结果看 Logcat 的 `SetText` tag：`matched` / `written` 表示是否找到并写入成功。


# 包含了项目 github.com/Genymobile/scrcpy 用于测试
# 我没域名，使用了example.com


adb shell "am broadcast -n com.example.nofocusinput/.DemoBroadcastReceiver -a com.example.nofocusinput.ACTION_TOGGLE_IME_HIDDEN"
切换键盘可用性





scrcpy --new-display --no-vd-system-decorations --start-app=com.huawei.calendar



python main.py --base-url https://open.bigmodel.cn/api/paas/v4 --model "autoglm-phone" --apikey bb3acbb12dff4e199ba83a04cc5e387e.OKtOpsZCEfT9I3Fm
