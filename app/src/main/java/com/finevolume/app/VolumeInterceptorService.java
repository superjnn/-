package com.finevolume.app;

import android.accessibilityservice.AccessibilityService;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * 核心：通过无障碍服务全局拦截音量键。
 *
 * 工作机制：
 *   1. 用户在系统设置里开启本应用的无障碍服务。
 *   2. 每当物理音量键被按下，onKeyEvent 会被回调，flagRequestFilterKeyEvents 让我们能「消费」该事件。
 *   3. 消费掉事件后，系统自带的音量步进就不会再触发，改由我们自己按映射算出来的 index 设置。
 *
 * 注意：Android 12+ 对无障碍拦截按键有更严格的限制，部分厂商 ROM 可能不派发音量键，
 * 此时 App 内测试按钮与悬浮窗（后续可扩展）仍可工作。
 */
public class VolumeInterceptorService extends AccessibilityService {

    private static volatile boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要处理事件，我们只关心按键。
    }

    @Override
    public void onInterrupt() {
        running = false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false;

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null || am.isVolumeFixed()) return false;

        VolumeMapping mapping = VolumeMapping.load(this);
        int stream = mapping.getTargetStream();
        int max = am.getStreamMaxVolume(stream);
        int current = am.getStreamVolume(stream);

        int virtual = mapping.realIndexToVirtual(current, max);
        virtual += (code == KeyEvent.KEYCODE_VOLUME_UP) ? 1 : -1;
        virtual = Math.max(0, Math.min(mapping.getVirtualSteps() - 1, virtual));

        int target = mapping.mapToRealIndex(virtual, max);
        target = Math.max(0, Math.min(max, target));

        am.setStreamVolume(stream, target,
                mapping.isShowSystemUi() ? AudioManager.FLAG_SHOW_UI : 0);
        // 仅在无障碍按键拦截确实可用时才消费事件（API 31+）；
        // 旧版本或厂商 ROM 限制时返回 false，系统原生步进会生效，
        // 但同一时刻 App 已写入目标值，效果等同于覆盖。
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S;
    }
}
