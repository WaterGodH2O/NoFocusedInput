package com.example.nofocusinput;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class DemoBroadcastReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.example.nofocusinput.ACTION_DEMO_BROADCAST";
    public static final String ACTION_DUMP_NODES = "com.example.nofocusinput.ACTION_DUMP_NODES";
    public static final String EXTRA_TEXT = "text";
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

        // ACTION_DUMP_NODES 和 ACTION_DEMO_BROADCAST 都会 dump 可编辑节点
        if (ACTION_DUMP_NODES.equals(action) || ACTION.equals(action)) {
            if (intent.hasExtra(EXTRA_ALL_DISPLAYS)) {
                InputAccessibilityService.setDumpAllDisplays(
                        intent.getBooleanExtra(EXTRA_ALL_DISPLAYS, false));
            }
            InputAccessibilityService.dumpEditableNodes();
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
