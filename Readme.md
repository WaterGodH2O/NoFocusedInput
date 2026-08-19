###NoFocusInput

这个项目利用无障碍服务，读取界面中的UI树，抓到允许ACTION_SET_TEXT的控件，并允许通过adb广播直接SETTEXT。由此达到不改变设备焦点的输入功能。



扫描当前UI树，结果以tag:NodeDump的日志输出
adb shell "am broadcast -a com.example.nofocusinput.ACTION_DUMP_NODES -n com.example.nofocusinput/.DemoBroadcastReceiver"

设置文本，允许使用全名和简写
com.huawei.calendar:id/title或者title都是可以的 这里使用的是华为的日历APP

adb shell "am broadcast -n com.example.nofocusinput/.DemoBroadcastReceiver -a com.example.nofocusinput.ACTION_SET_TEXT --es id 'title' --es text 'hello'"



注意命令使用双引号，参数使用单引号，不然空格会被错误解析