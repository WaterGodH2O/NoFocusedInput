package com.example.nofocusinput;

import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** 无障碍悬浮窗：点击翻转软键盘可见性，拖动可移动。 */
final class ImeOverlay {

    private static final int DRAG_SLOP_PX = 12;

    private final InputAccessibilityService service;
    private final WindowManager windowManager;
    private View root;
    private TextView label;
    private WindowManager.LayoutParams params;
    private float downRawX;
    private float downRawY;
    private int downParamX;
    private int downParamY;
    private boolean dragged;

    ImeOverlay(InputAccessibilityService service) {
        this.service = service;
        this.windowManager = (WindowManager) service.getSystemService(
                InputAccessibilityService.WINDOW_SERVICE);
    }

    boolean isShowing() {
        return root != null;
    }

    void show() {
        if (root != null || windowManager == null) {
            return;
        }
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 40;
        params.y = 240;
        root = LayoutInflater.from(service).inflate(R.layout.overlay_ime_toggle, null);
        label = root.findViewById(R.id.overlay_ime_label);
        refreshLabel();
        root.setOnTouchListener(this::onTouch);
        windowManager.addView(root, params);
    }

    void hide() {
        if (root == null || windowManager == null) {
            return;
        }
        windowManager.removeView(root);
        root = null;
        label = null;
        params = null;
    }

    void refreshLabel() {
        if (label == null) {
            return;
        }
        label.setText(InputAccessibilityService.isKeyboardVisible()
                ? R.string.overlay_keyboard_on
                : R.string.overlay_keyboard_off);
    }

    private boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragged = false;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downParamX = params.x;
                downParamY = params.y;
                return true;
            case MotionEvent.ACTION_MOVE: {
                int dx = Math.round(event.getRawX() - downRawX);
                int dy = Math.round(event.getRawY() - downRawY);
                if (Math.abs(dx) > DRAG_SLOP_PX || Math.abs(dy) > DRAG_SLOP_PX) {
                    dragged = true;
                }
                if (dragged) {
                    params.x = downParamX + dx;
                    params.y = downParamY + dy;
                    windowManager.updateViewLayout(root, params);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!dragged) {
                    InputAccessibilityService.toggleSoftKeyboardHidden();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return false;
        }
    }
}
