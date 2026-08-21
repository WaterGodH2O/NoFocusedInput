package com.example.nofocusinput;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;

import java.util.ArrayList;

public class DemoBroadcastReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.example.nofocusinput.ACTION_DEMO_BROADCAST";
    public static final String ACTION_DUMP_NODES = "com.example.nofocusinput.ACTION_DUMP_NODES";
    /**
     * dump 完成后发出的结果广播，供另一个 App 接收可写入控件的完整 id 列表。
     * extra {@link #EXTRA_VIEW_IDS}：ArrayList&lt;String&gt;，元素形如 pkg:id/name。
     */
    public static final String ACTION_EDITABLE_IDS = "com.example.nofocusinput.ACTION_EDITABLE_IDS";
    public static final String ACTION_SET_TEXT = "com.example.nofocusinput.ACTION_SET_TEXT";
    /** 按屏幕坐标找到可写入节点并 SET_TEXT。 */
    public static final String ACTION_SET_TEXT_BY_POINT =
            "com.example.nofocusinput.ACTION_SET_TEXT_BY_POINT";
    /** 翻转软键盘隐藏：SHOW_MODE_HIDDEN ↔ SHOW_MODE_AUTO。 */
    public static final String ACTION_TOGGLE_IME_HIDDEN =
            "com.example.nofocusinput.ACTION_TOGGLE_IME_HIDDEN";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_VIEW_ID = "id";
    /** {@link #ACTION_SET_TEXT_BY_POINT} 的屏幕 X 坐标（像素）。 */
    public static final String EXTRA_X = "x";
    /** {@link #ACTION_SET_TEXT_BY_POINT} 的屏幕 Y 坐标（像素）。 */
    public static final String EXTRA_Y = "y";
    /**
     * {@link #ACTION_SET_TEXT_BY_POINT} 的逻辑屏 id，与 dump 日志里的 displayId 相同。
     * 省略或空字符串时使用主屏 {@link Display#DEFAULT_DISPLAY}（0）。
     */
    public static final String EXTRA_DISPLAY_ID = "displayId";
    /** {@link #ACTION_EDITABLE_IDS} 携带的完整资源 id 列表。 */
    public static final String EXTRA_VIEW_IDS = "ids";
    public static final String EXTRA_ALL_DISPLAYS = "allDisplays";
    private static final String TAG = "DemoBroadcast";

    public interface Listener {
        void onBroadcast(String text);
    }

    private static Listener listener;
    private static String lastText;

    public static void setListener(Listener newListener) {
        listener = newListener;
        if (newListener != null && lastText != null) {
            newListener.onBroadcast(lastText);
        }
    }

    public static String getLastText() {
        return lastText;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "received action=" + action + " serviceRunning=" + InputAccessibilityService.isRunning());

        if (intent.hasExtra(EXTRA_ALL_DISPLAYS)) {
            InputAccessibilityService.setDumpAllDisplays(
                    intent.getBooleanExtra(EXTRA_ALL_DISPLAYS, false));
        }

        if (ACTION_TOGGLE_IME_HIDDEN.equals(action)) {
            InputAccessibilityService.toggleSoftKeyboardHidden();
            return;
        }

        // 按 id 找到输入框并写入 text，不更新本 App 的 Demo 输入框
        if (ACTION_SET_TEXT.equals(action)) {
            String viewId = intent.getStringExtra(EXTRA_VIEW_ID);
            if (viewId == null || viewId.isEmpty()) {
                Log.w(TAG, "skip setText: missing extra id");
                return;
            }
            if (!intent.hasExtra(EXTRA_TEXT)) {
                Log.w(TAG, "skip setText: missing extra text");
                return;
            }
            String text = readPayload(intent);
            InputAccessibilityService.setTextByViewId(viewId, text);
            return;
        }

        // 按屏幕坐标命中可写入节点并写入 text
        if (ACTION_SET_TEXT_BY_POINT.equals(action)) {
            Integer x = readCoord(intent, EXTRA_X);
            Integer y = readCoord(intent, EXTRA_Y);
            if (x == null || y == null) {
                Log.w(TAG, "skip setTextByPoint: missing extra x or y");
                return;
            }
            if (!intent.hasExtra(EXTRA_TEXT)) {
                Log.w(TAG, "skip setTextByPoint: missing extra text");
                return;
            }
            String text = readPayload(intent);
            int displayId = readDisplayId(intent);
            Log.i(TAG, "setTextByPoint x=" + x + " y=" + y + " displayId=" + displayId);
            InputAccessibilityService.setTextByPoint(x, y, displayId, text);
            return;
        }

        // ACTION_DUMP_NODES 和 ACTION_DEMO_BROADCAST 都会 dump 可编辑节点
        if (ACTION_DUMP_NODES.equals(action) || ACTION.equals(action)) {
            ArrayList<String> editableIds = InputAccessibilityService.dumpEditableNodes();
            // 只在 DUMP 请求时把 id 列表广播出去，避免 DEMO 广播也打扰其他 App
            if (ACTION_DUMP_NODES.equals(action)) {
                sendEditableIds(context, editableIds);
            }
        }

        // 只有 DEMO 广播才继续更新 UI；纯 dump 到这里结束
        if (!ACTION.equals(action)) {
            return;
        }

        String text = readPayload(intent);
        lastText = text; // 页面稍后回来时还能显示上次内容
        Log.i(TAG, "received text: " + text);
        if (listener != null) {
            listener.onBroadcast(text);
        } else {
            // 页面在后台或已销毁时不更新输入框，dump 已经在上面跑过
            Log.w(TAG, "UI listener is null (app in background); dump still ran");
        }
    }

    /**
     * 把本次 dump 到的完整资源 id 发给其他 App。
     * 对方监听 {@link #ACTION_EDITABLE_IDS}，用 getStringArrayListExtra({@link #EXTRA_VIEW_IDS}) 取出。
     * Android 8+ 上建议对方用 registerReceiver 动态注册；清单静态注册可能收不到隐式广播。
     */
    private static void sendEditableIds(Context context, ArrayList<String> editableIds) {
        Intent out = new Intent(ACTION_EDITABLE_IDS);
        out.putStringArrayListExtra(EXTRA_VIEW_IDS, editableIds);
        context.sendBroadcast(out);
        Log.i(TAG, "sent " + ACTION_EDITABLE_IDS + " count=" + editableIds.size()
                + " ids=" + editableIds);
    }

    /** 省略、空字符串或无法解析时落到主屏。 */
    private static int readDisplayId(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(EXTRA_DISPLAY_ID)) {
            return Display.DEFAULT_DISPLAY;
        }
        Object raw = extras.get(EXTRA_DISPLAY_ID);
        if (raw == null) {
            return Display.DEFAULT_DISPLAY;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) {
            return Display.DEFAULT_DISPLAY;
        }
        try {
            return (int) Float.parseFloat(value);
        } catch (NumberFormatException e) {
            Log.w(TAG, "invalid displayId=" + raw + ", fallback to default display");
            return Display.DEFAULT_DISPLAY;
        }
    }

    /** 读整数或浮点 extra，截成屏幕像素；缺省或无法解析时返回 null。 */
    private static Integer readCoord(Intent intent, String key) {
        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(key)) {
            return null;
        }
        Object raw = extras.get(key);
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return (int) Float.parseFloat(String.valueOf(raw));
        } catch (NumberFormatException e) {
            Log.w(TAG, "skip setTextByPoint: extra " + key + " is not a number: " + raw);
            return null;
        }
    }

    private static String readPayload(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null || extras.isEmpty()) {
            return "(no extras)";
        }
        if (extras.containsKey(EXTRA_TEXT)) {
            CharSequence value = extras.getCharSequence(EXTRA_TEXT);
            if (value != null) {
                return value.toString();
            }
            Object raw = extras.get(EXTRA_TEXT);
            return raw != null ? String.valueOf(raw) : "";
        }
        StringBuilder lines = new StringBuilder();
        for (String key : extras.keySet()) {
            lines.append(key).append(": ").append(extras.get(key)).append('\n');
        }
        return lines.toString().trim();
    }
}
