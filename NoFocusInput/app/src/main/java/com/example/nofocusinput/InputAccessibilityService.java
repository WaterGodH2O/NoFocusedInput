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
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

public class InputAccessibilityService extends AccessibilityService {

    private static final String TAG = "InputA11y";
    private static final String DUMP_TAG = "NodeDump";
    private static final String SET_TAG = "SetText";
    private static final String IME_TAG = "ImeMode";

    private static InputAccessibilityService instance;
    private static boolean dumpAllDisplays = true;
    /** 软键盘是否允许显示：非 {@link #SHOW_MODE_HIDDEN} 即为可见。 */
    private static boolean keyboardVisible = true;
    private ImeOverlay imeOverlay;

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
        keyboardVisible = isImeAllowedVisible(getSoftKeyboardController().getShowMode());
        Log.i(TAG, "service connected keyboardVisible=" + keyboardVisible);
    }

    @Override
    public void onDestroy() {
        hideImeOverlayInternal();
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    public static boolean isRunning() {
        return instance != null;
    }

    /** 软键盘当前是否允许显示（SHOW_MODE 非 HIDDEN）。 */
    public static boolean isKeyboardVisible() {
        return keyboardVisible;
    }

    /**
     * 软键盘是否处于隐藏（SHOW_MODE_HIDDEN）。
     * 与 {@link #toggleSoftKeyboardHidden()} 读同一份状态，只读不改。
     */
    public static boolean isImeHidden() {
        InputAccessibilityService service = instance;
        if (service != null) {
            return !isImeAllowedVisible(service.getSoftKeyboardController().getShowMode());
        }
        return !keyboardVisible;
    }

    public static boolean isImeOverlayShowing() {
        return instance != null && instance.imeOverlay != null && instance.imeOverlay.isShowing();
    }

    /**
     * 弹出键盘控制悬浮窗，并把 IME 设为可见（SHOW_MODE_AUTO）。
     * @return 无障碍服务未连接时返回 false。
     */
    public static boolean showImeOverlay() {
        InputAccessibilityService service = instance;
        if (service == null) {
            Log.w(IME_TAG, "skip show overlay: accessibility service not connected");
            return false;
        }
        service.setKeyboardVisible(true);
        if (service.imeOverlay == null) {
            service.imeOverlay = new ImeOverlay(service);
        }
        service.imeOverlay.show();
        return true;
    }

    public static void hideImeOverlay() {
        if (instance != null) {
            instance.hideImeOverlayInternal();
        }
    }

    /**
     * 在 SHOW_MODE_HIDDEN 与 SHOW_MODE_AUTO 之间切换，并更新 {@link #keyboardVisible}。
     */
    public static void toggleSoftKeyboardHidden() {
        InputAccessibilityService service = instance;
        if (service == null) {
            Log.w(IME_TAG, "skip toggle IME: accessibility service not connected");
            return;
        }
        boolean visible = isImeAllowedVisible(service.getSoftKeyboardController().getShowMode());
        service.setKeyboardVisible(!visible);
    }

    private void hideImeOverlayInternal() {
        if (imeOverlay != null) {
            imeOverlay.hide();
        }
    }

    private void setKeyboardVisible(boolean visible) {
        SoftKeyboardController ime = getSoftKeyboardController();
        int current = ime.getShowMode();
        int next = visible ? SHOW_MODE_AUTO : SHOW_MODE_HIDDEN;
        boolean ok = ime.setShowMode(next);
        int applied = ime.getShowMode();
        keyboardVisible = isImeAllowedVisible(applied);
        Log.i(IME_TAG, "softKeyboardShowMode " + modeName(current) + " -> " + modeName(applied)
                + " visible=" + keyboardVisible + " ok=" + ok);
        if (imeOverlay != null) {
            imeOverlay.refreshLabel();
        }
    }

    private static boolean isImeAllowedVisible(int showMode) {
        return showMode != SHOW_MODE_HIDDEN;
    }

    private static String modeName(int mode) {
        switch (mode) {
            case SHOW_MODE_AUTO:
                return "AUTO";
            case SHOW_MODE_HIDDEN:
                return "HIDDEN";
            case SHOW_MODE_IGNORE_HARD_KEYBOARD:
                return "IGNORE_HARD_KEYBOARD";
            default:
                return "UNKNOWN(" + mode + ")";
        }
    }

    /**
     * 对外入口：无障碍服务已连接时，把当前屏幕上可写入的节点打到 Logcat。
     * @return 可 SET_TEXT 且带完整资源 id 的控件列表（pkg:id/name）；服务未连接时返回空列表。
     */
    public static ArrayList<String> dumpEditableNodes() {
        ArrayList<String> editableIds = new ArrayList<>();
        InputAccessibilityService service = instance;
        if (service == null) {
            Log.w(DUMP_TAG, "skip dump: accessibility service not connected");
            return editableIds;
        }
        service.dumpEditableNodesInternal(editableIds);
        return editableIds;
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
     * 按屏幕坐标找到可写入节点并设置文本。
     * (x, y) 与 {@link AccessibilityNodeInfo#getBoundsInScreen} 同一套像素坐标，属于 displayId 这块屏。
     * displayId 用 {@link Display#DEFAULT_DISPLAY} 表示主屏，其它值只扫对应那块屏。
     */
    public static void setTextByPoint(int x, int y, int displayId, String text) {
        InputAccessibilityService service = instance;
        if (service == null) {
            Log.w(SET_TAG, "skip setTextByPoint: accessibility service not connected");
            return;
        }
        service.setTextByPointInternal(x, y, displayId, text == null ? "" : text);
    }

    /**
     * 按 dumpAllDisplays 选择扫默认屏还是所有屏。
     * counts[0] 扫过的节点数，counts[1] 其中可 setText 的节点数。
     */
    private void dumpEditableNodesInternal(ArrayList<String> editableIds) {
        int[] counts = new int[] {0, 0};
        if (dumpAllDisplays) {
            dumpWindowsOnAllDisplays(counts, editableIds);
        } else {
            dumpDefaultDisplay(counts, editableIds);
        }
        Log.i(DUMP_TAG, "done mode=" + (dumpAllDisplays ? "allDisplays" : "default")
                + " scanned=" + counts[0] + " setText=" + counts[1]
                + " ids=" + editableIds.size());
    }

    /** 扫默认显示器上的窗口；拿不到窗口列表时退回当前活动窗口根节点。 */
    private void dumpDefaultDisplay(int[] counts, ArrayList<String> editableIds) {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null && !windows.isEmpty()) {
            Log.i(DUMP_TAG, "windows=" + windows.size());
            dumpWindowList(windows, counts, editableIds);
        } else {
            Log.i(DUMP_TAG, "windows empty, fallback to active root");
            dumpNode(getRootInActiveWindow(), 0, counts, editableIds);
        }
    }

    /** API 30+ 按显示器分组扫所有窗口；版本不够或结果为空时退回默认屏。 */
    private void dumpWindowsOnAllDisplays(int[] counts, ArrayList<String> editableIds) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(DUMP_TAG, "allDisplays requires API 30, fallback to default display");
            dumpDefaultDisplay(counts, editableIds);
            return;
        }
        SparseArray<List<AccessibilityWindowInfo>> byDisplay = getWindowsOnAllDisplays();
        if (byDisplay == null || byDisplay.size() == 0) {
            Log.i(DUMP_TAG, "allDisplays empty, fallback to default display");
            dumpDefaultDisplay(counts, editableIds);
            return;
        }
        Log.i(DUMP_TAG, "displays=" + byDisplay.size());
        for (int i = 0; i < byDisplay.size(); i++) {
            int displayId = byDisplay.keyAt(i);
            List<AccessibilityWindowInfo> windows = byDisplay.valueAt(i);
            Log.i(DUMP_TAG, "displayId=" + displayId
                    + " windows=" + (windows == null ? 0 : windows.size()));
            dumpWindowList(windows, counts, editableIds);
        }
    }

    /** 遍历一组窗口，打印窗口信息，再从每个窗口的根节点开始扫控件树。 */
    private void dumpWindowList(List<AccessibilityWindowInfo> windows, int[] counts,
            ArrayList<String> editableIds) {
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
            dumpNode(window.getRoot(), 0, counts, editableIds);
        }
    }

    /** 递归遍历节点树：统计节点数，可写入的才打印详情并收集完整资源 id。 */
    private void dumpNode(AccessibilityNodeInfo node, int depth, int[] counts,
            ArrayList<String> editableIds) {
        if (node == null) {
            return;
        }
        counts[0]++;
        if (canSetText(node)) {
            counts[1]++;
            Log.i(DUMP_TAG, formatNode(node, depth));
            String viewId = node.getViewIdResourceName();
            if (viewId != null && !viewId.isEmpty()) {
                editableIds.add(viewId);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            dumpNode(node.getChild(i), depth + 1, counts, editableIds);
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

    /**
     * 在 displayId 这块屏上命中包含 (x, y) 的可写入节点。
     * 不按窗口矩形预过滤：部分机型 window bounds 是空的或和节点 bounds 不在同一套坐标。
     */
    private void setTextByPointInternal(int x, int y, int displayId, String text) {
        Log.i(SET_TAG, "setTextByPoint x=" + x + " y=" + y
                + " displayId=" + displayId + " text=" + quote(text));
        AccessibilityNodeInfo target = findWritableAtOnDisplay(displayId, x, y);
        if (target == null) {
            Log.w(SET_TAG, "done byPoint x=" + x + " y=" + y
                    + " displayId=" + displayId + " matched=0 written=0");
            return;
        }
        Log.i(SET_TAG, "matched " + formatNode(target, 0));
        if (performSetText(target, text)) {
            Log.i(SET_TAG, "written id=" + target.getViewIdResourceName());
            Log.i(SET_TAG, "done byPoint x=" + x + " y=" + y
                    + " displayId=" + displayId + " matched=1 written=1");
        } else {
            Log.w(SET_TAG, "performAction failed id=" + target.getViewIdResourceName());
            Log.w(SET_TAG, "done byPoint x=" + x + " y=" + y
                    + " displayId=" + displayId + " matched=1 written=0");
        }
    }

    private AccessibilityNodeInfo findWritableAtOnDisplay(int displayId, int x, int y) {
        List<AccessibilityWindowInfo> windows = windowsOnDisplay(displayId);
        int windowCount = windows == null ? 0 : windows.size();
        Log.i(SET_TAG, "scan displayId=" + displayId + " windows=" + windowCount);
        AccessibilityNodeInfo found = findWritableAtInWindowList(windows, x, y);
        if (found != null) {
            return found;
        }
        if (displayId == Display.DEFAULT_DISPLAY) {
            Log.i(SET_TAG, "no window-list hit, fallback to active root");
            return findWritableAtInTree(getRootInActiveWindow(), x, y);
        }
        return null;
    }

    /**
     * 主屏走 {@link #getWindows()}；其它屏按 displayId 从 {@link #getWindowsOnAllDisplays()} 取。
     */
    private List<AccessibilityWindowInfo> windowsOnDisplay(int displayId) {
        if (displayId == Display.DEFAULT_DISPLAY) {
            return getWindows();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(SET_TAG, "displayId requires API 30, skip displayId=" + displayId);
            return null;
        }
        SparseArray<List<AccessibilityWindowInfo>> byDisplay = getWindowsOnAllDisplays();
        if (byDisplay != null && byDisplay.size() > 0) {
            List<AccessibilityWindowInfo> windows = byDisplay.get(displayId);
            if (windows != null && !windows.isEmpty()) {
                return windows;
            }
            Log.w(SET_TAG, "displayId=" + displayId + " has no windows, available="
                    + availableDisplayIds(byDisplay));
        }
        return windowsWithDisplayId(getWindows(), displayId);
    }

    private static String availableDisplayIds(SparseArray<List<AccessibilityWindowInfo>> byDisplay) {
        StringBuilder ids = new StringBuilder("[");
        for (int i = 0; i < byDisplay.size(); i++) {
            if (i > 0) {
                ids.append(',');
            }
            ids.append(byDisplay.keyAt(i));
        }
        return ids.append(']').toString();
    }

    private static List<AccessibilityWindowInfo> windowsWithDisplayId(
            List<AccessibilityWindowInfo> windows, int displayId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || windows == null || windows.isEmpty()) {
            return null;
        }
        List<AccessibilityWindowInfo> matched = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window != null && window.getDisplayId() == displayId) {
                matched.add(window);
            }
        }
        return matched.isEmpty() ? null : matched;
    }

    /** 窗口按 Z 序从前到后；命中最前层里包含该点的可写入节点。 */
    private AccessibilityNodeInfo findWritableAtInWindowList(List<AccessibilityWindowInfo> windows,
            int x, int y) {
        if (windows == null || windows.isEmpty()) {
            return null;
        }
        AccessibilityNodeInfo found = findWritableAtInWindowList(windows, x, y, true);
        if (found != null) {
            return found;
        }
        Log.i(SET_TAG, "no visibleToUser match, retry including hidden nodes");
        return findWritableAtInWindowList(windows, x, y, false);
    }

    private AccessibilityNodeInfo findWritableAtInWindowList(List<AccessibilityWindowInfo> windows,
            int x, int y, boolean visibleOnly) {
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window == null) {
                continue;
            }
            AccessibilityNodeInfo found = findWritableAtInTree(window.getRoot(), x, y, visibleOnly);
            if (found != null) {
                String displayPart = "";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    displayPart = " displayId=" + window.getDisplayId();
                }
                Log.i(SET_TAG, "hit window[" + i + "] type=" + window.getType()
                        + " title=" + window.getTitle()
                        + " active=" + window.isActive()
                        + displayPart);
                return found;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findWritableAtInTree(AccessibilityNodeInfo node, int x, int y) {
        AccessibilityNodeInfo found = findWritableAtInTree(node, x, y, true);
        if (found != null) {
            return found;
        }
        return findWritableAtInTree(node, x, y, false);
    }

    /** 在包含 (x, y) 的可写入节点里取面积最小的一个。 */
    private AccessibilityNodeInfo findWritableAtInTree(AccessibilityNodeInfo node, int x, int y,
            boolean visibleOnly) {
        NodeHit hit = new NodeHit();
        collectWritableAt(node, x, y, hit, visibleOnly);
        return hit.node;
    }

    private void collectWritableAt(AccessibilityNodeInfo node, int x, int y, NodeHit hit,
            boolean visibleOnly) {
        if (node == null) {
            return;
        }
        if (canSetText(node) && (!visibleOnly || node.isVisibleToUser())) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (pointInBounds(bounds, x, y)) {
                int area = bounds.width() * bounds.height();
                if (area > 0 && area < hit.area) {
                    hit.node = node;
                    hit.area = area;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectWritableAt(node.getChild(i), x, y, hit, visibleOnly);
        }
    }

    /** 点击坐标按闭区间，避免落在 Rect.contains 右侧/下侧开边界上时 miss。 */
    private static boolean pointInBounds(Rect bounds, int x, int y) {
        return x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom;
    }

    private static final class NodeHit {
        AccessibilityNodeInfo node;
        int area = Integer.MAX_VALUE;
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
