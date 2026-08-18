package com.example.nofocusinput;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

public class InputAccessibilityService extends AccessibilityService {

    private static final String TAG = "InputA11y";
    private static final String DUMP_TAG = "NodeDump";
    private static final String SET_TAG = "SetText";

    private static InputAccessibilityService instance;
    private static boolean dumpAllDisplays = false;

    public static void setDumpAllDisplays(boolean allDisplays) {
        dumpAllDisplays = allDisplays;
        Log.i(DUMP_TAG, "dumpAllDisplays=" + dumpAllDisplays);
    }

    public static boolean isDumpAllDisplays() {
        return dumpAllDisplays;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "service connected");
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    public static boolean isRunning() {
        return instance != null;
    }

    /** 对外入口：无障碍服务已连接时，把当前屏幕上可写入的节点打到 Logcat。 */
    public static void dumpEditableNodes() {
        InputAccessibilityService service = instance;
        if (service == null) {
            Log.w(DUMP_TAG, "skip dump: accessibility service not connected");
            return;
        }
        
        service.dumpEditableNodesInternal();
    }

    /**
     * 按 viewId 找到可写入节点并设置文本。
     * id 可以是完整资源名（pkg:id/name）或短名（name）。
     */
    public static void setTextByViewId(String viewId, String text) {
        if (viewId == null || viewId.isEmpty()) {
            Log.w(SET_TAG, "skip setText: id is empty");
            return;
        }
        InputAccessibilityService service = instance;
        if (service == null) {
            Log.w(SET_TAG, "skip setText: accessibility service not connected");
            return;
        }
        service.setTextByViewIdInternal(viewId, text == null ? "" : text);
    }

    /**
     * 按 dumpAllDisplays 选择扫默认屏还是所有屏。
     * counts[0] 扫过的节点数，counts[1] 其中可 setText 的节点数。
     */
    private void dumpEditableNodesInternal() {
        int[] counts = new int[] {0, 0};
        if (dumpAllDisplays) {
            dumpWindowsOnAllDisplays(counts);
        } else {
            dumpDefaultDisplay(counts);
        }
        Log.i(DUMP_TAG, "done mode=" + (dumpAllDisplays ? "allDisplays" : "default")
                + " scanned=" + counts[0] + " setText=" + counts[1]);
    }

    /** 扫默认显示器上的窗口；拿不到窗口列表时退回当前活动窗口根节点。 */
    private void dumpDefaultDisplay(int[] counts) {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null && !windows.isEmpty()) {
            Log.i(DUMP_TAG, "windows=" + windows.size());
            dumpWindowList(windows, counts);
        } else {
            Log.i(DUMP_TAG, "windows empty, fallback to active root");
            dumpNode(getRootInActiveWindow(), 0, counts);
        }
    }

    /** API 30+ 按显示器分组扫所有窗口；版本不够或结果为空时退回默认屏。 */
    private void dumpWindowsOnAllDisplays(int[] counts) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(DUMP_TAG, "allDisplays requires API 30, fallback to default display");
            dumpDefaultDisplay(counts);
            return;
        }
        SparseArray<List<AccessibilityWindowInfo>> byDisplay = getWindowsOnAllDisplays();
        if (byDisplay == null || byDisplay.size() == 0) {
            Log.i(DUMP_TAG, "allDisplays empty, fallback to default display");
            dumpDefaultDisplay(counts);
            return;
        }
        Log.i(DUMP_TAG, "displays=" + byDisplay.size());
        for (int i = 0; i < byDisplay.size(); i++) {
            int displayId = byDisplay.keyAt(i);
            List<AccessibilityWindowInfo> windows = byDisplay.valueAt(i);
            Log.i(DUMP_TAG, "displayId=" + displayId
                    + " windows=" + (windows == null ? 0 : windows.size()));
            dumpWindowList(windows, counts);
        }
    }

    /** 遍历一组窗口，打印窗口信息，再从每个窗口的根节点开始扫控件树。 */
    private void dumpWindowList(List<AccessibilityWindowInfo> windows, int[] counts) {
        if (windows == null) {
            return;
        }
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window == null) {
                continue;
            }
            String displayPart = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                displayPart = " displayId=" + window.getDisplayId();
            }
            Log.i(DUMP_TAG, "window[" + i + "] type=" + window.getType()
                    + " title=" + window.getTitle()
                    + " active=" + window.isActive()
                    + displayPart);
            dumpNode(window.getRoot(), 0, counts);
        }
    }

    /** 递归遍历节点树：统计节点数，可写入的才打印详情。 */
    private void dumpNode(AccessibilityNodeInfo node, int depth, int[] counts) {
        if (node == null) {
            return;
        }
        counts[0]++;
        if (canSetText(node)) {
            counts[1]++;
            Log.i(DUMP_TAG, formatNode(node, depth));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            dumpNode(node.getChild(i), depth + 1, counts);
        }
    }

    /** 节点是否可被无障碍写入：启用，且 isEditable 或支持 ACTION_SET_TEXT。 */
    private static boolean canSetText(AccessibilityNodeInfo node) {
        if (!node.isEnabled()) {
            return false;
        }
        if (node.isEditable()) {
            return true;
        }
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        if (actions != null) {
            for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
                if (action.getId() == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                    return true;
                }
            }
        }
        return (node.getActions() & AccessibilityNodeInfo.ACTION_SET_TEXT) != 0;
    }

    /** 把可写入节点的包名、类名、id、文字、坐标等拼成一条日志。 */
    private static String formatNode(AccessibilityNodeInfo node, int depth) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        CharSequence hint = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hint = node.getHintText();
        }
        return "depth=" + depth
                + " pkg=" + node.getPackageName()
                + " class=" + node.getClassName()
                + " id=" + node.getViewIdResourceName()
                + " text=" + quote(node.getText())
                + " hint=" + quote(hint)
                + " desc=" + quote(node.getContentDescription())
                + " bounds=" + bounds.toShortString()
                + " focused=" + node.isFocused()
                + " password=" + node.isPassword()
                + " editable=" + node.isEditable();
    }

    /**
     * 按 dumpAllDisplays 选择扫哪些窗口。
     * counts[0] 匹配到的 id 数量，counts[1] 写入成功数量。
     */
    private void setTextByViewIdInternal(String viewId, String text) {
        String id = normalizeViewId(viewId);
        int[] counts = new int[] {0, 0};
        Log.i(SET_TAG, "setText id=" + id + " text=" + quote(text)
                + " mode=" + (dumpAllDisplays ? "allDisplays" : "default"));
        if (dumpAllDisplays) {
            setTextOnAllDisplays(id, text, counts);
        } else {
            setTextOnDefaultDisplay(id, text, counts);
        }
        Log.i(SET_TAG, "done id=" + id + " matched=" + counts[0] + " written=" + counts[1]);
    }

    private void setTextOnDefaultDisplay(String viewId, String text, int[] counts) {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null && !windows.isEmpty()) {
            setTextInWindowList(windows, viewId, text, counts);
        } else {
            setTextInTree(getRootInActiveWindow(), viewId, text, counts);
        }
    }

    private void setTextOnAllDisplays(String viewId, String text, int[] counts) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(SET_TAG, "allDisplays requires API 30, fallback to default display");
            setTextOnDefaultDisplay(viewId, text, counts);
            return;
        }
        SparseArray<List<AccessibilityWindowInfo>> byDisplay = getWindowsOnAllDisplays();
        if (byDisplay == null || byDisplay.size() == 0) {
            setTextOnDefaultDisplay(viewId, text, counts);
            return;
        }
        for (int i = 0; i < byDisplay.size(); i++) {
            setTextInWindowList(byDisplay.valueAt(i), viewId, text, counts);
        }
    }

    private void setTextInWindowList(List<AccessibilityWindowInfo> windows, String viewId,
            String text, int[] counts) {
        if (windows == null) {
            return;
        }
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window == null) {
                continue;
            }
            setTextInTree(window.getRoot(), viewId, text, counts);
        }
    }

    /** 递归找匹配 id 的节点；可写入则执行 ACTION_SET_TEXT。 */
    private void setTextInTree(AccessibilityNodeInfo node, String viewId, String text, int[] counts) {
        if (node == null) {
            return;
        }
        if (idMatches(node, viewId)) {
            counts[0]++;
            Log.i(SET_TAG, "matched " + formatNode(node, 0));
            if (!canSetText(node)) {
                Log.w(SET_TAG, "matched but not writable id=" + node.getViewIdResourceName());
            } else if (performSetText(node, text)) {
                counts[1]++;
                Log.i(SET_TAG, "written id=" + node.getViewIdResourceName());
            } else {
                Log.w(SET_TAG, "performAction failed id=" + node.getViewIdResourceName());
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            setTextInTree(node.getChild(i), viewId, text, counts);
        }
    }

    private static boolean performSetText(AccessibilityNodeInfo node, String text) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private static String normalizeViewId(String viewId) {
        if (viewId.startsWith("@+id/")) {
            return viewId.substring(5);
        }
        if (viewId.startsWith("@id/")) {
            return viewId.substring(4);
        }
        if (viewId.startsWith("id/")) {
            return viewId.substring(3);
        }
        return viewId;
    }

    /** 完整资源名精确匹配；短名匹配资源名最后一段。 */
    private static boolean idMatches(AccessibilityNodeInfo node, String wanted) {
        String actual = node.getViewIdResourceName();
        if (actual == null || wanted.isEmpty()) {
            return false;
        }
        if (actual.equals(wanted)) {
            return true;
        }
        int slash = actual.lastIndexOf('/');
        String shortId = slash >= 0 ? actual.substring(slash + 1) : actual;
        return wanted.equals(shortId);
    }

    private static String quote(CharSequence value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.toString().replace("\n", "\\n") + "\"";
    }

    public static boolean isEnabled(Context context) {
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        List<AccessibilityServiceInfo> enabled =
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String expected = new ComponentName(context, InputAccessibilityService.class).flattenToShortString();
        for (AccessibilityServiceInfo info : enabled) {
            if (expected.equals(info.getId())) {
                return true;
            }
        }
        return false;
    }
}
