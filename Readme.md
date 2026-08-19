# NoFocusInput

通过无障碍服务读取当前界面控件树，找出支持 `ACTION_SET_TEXT` 的输入框，再用 ADB 广播写入文本。写入时不抢设备焦点、不唤起输入法。

## 使用前

1. 安装并打开本 App。
2. 在系统设置中开启无障碍服务 **无焦点输入**（App 内可点「打开无障碍设置」）。
3. 目标输入框需出现在当前屏幕上（可无焦点）。

查看相关日志：

```bash
adb logcat -s NodeDump:I SetText:I DemoBroadcast:I
```

## 扫描可写入控件

遍历当前界面（含多显示器窗口），只把**可 `SET_TEXT`** 的节点打到 Logcat，tag 为 `NodeDump`。

```bash
adb shell "am broadcast -n com.example.nofocusinput/.DemoBroadcastReceiver -a com.example.nofocusinput.ACTION_DUMP_NODES"
```

日志里可直接看到 `id`、`class`、`text`、`bounds`、`focused` 等，供下一步填写 `--es id`。

## 写入文本

`--es id` 支持完整资源名或短名，例如华为日历标题框：

| 写法 | 示例 |
|------|------|
| 完整 id | `com.huawei.calendar:id/title` |
| 短名 | `title` |

```bash
adb shell "am broadcast -n com.example.nofocusinput/.DemoBroadcastReceiver -a com.example.nofocusinput.ACTION_SET_TEXT --es id 'title' --es text 'hello'"
```

短名可能命中多个同名控件，会全部写入。需要精确匹配时用完整 id。

## 命令注意事项

- 整条 `adb shell "..."` 用**双引号**包住；`--es` 的值用**单引号**，否则空格会被 shell 拆开。
- 含空格的文本示例：`--es text 'hello world'`
- 广播结果看 Logcat 的 `SetText` tag：`matched` / `written` 表示是否找到并写入成功。
