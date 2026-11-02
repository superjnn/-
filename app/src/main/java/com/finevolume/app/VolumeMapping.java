package com.finevolume.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 分段映射模型。
 *
 * 设计思路：
 *   - 把系统音量 0..maxVolume 等分成 virtualSteps 个「虚拟档位」。
 *   - 每个虚拟档位有一个自己的 0..100 虚拟进度 p。
 *   - 按分段倍率把 p 映射成最终「请求进度」r：
 *       p 落在第 i 段 [a_i, b_i]，段内局部进度 t=(p-a_i)/(b_i-a_i)
 *       段内局部目标 t' = t * c_i        （c_i 为该段倍率）
 *       r = a_i + t' * (b_i - a_i)
 *   - 每按一次音量键，虚拟档位 +1 或 -1，再按当前映射换算成真实系统 index。
 *
 * 倍率 c_i 的直观含义：
 *     c_i = 0.6  → 这一段的旋钮被「拧小」，跨过这段只动真实音量的 60%
 *     c_i = 2.0  → 这一段被「放大」，跨过这段真实音量走 200%（可快速拉满）
 *     c_i = 1.0  → 与系统原生线性步进一致
 */
public class VolumeMapping {

    /** 一个区间：虚拟进度 [lower, upper]，倍率 multiplier。 */
    public static class Stage {
        public int lower;      // 含
        public int upper;      // 含
        public float multiplier;

        public Stage(int lower, int upper, float multiplier) {
            this.lower = lower;
            this.upper = upper;
            this.multiplier = multiplier;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("lower", lower);
            o.put("upper", upper);
            o.put("multiplier", multiplier);
            return o;
        }

        static Stage fromJson(JSONObject o) throws JSONException {
            return new Stage(o.getInt("lower"), o.getInt("upper"), (float) o.getDouble("multiplier"));
        }
    }

    public static final int DEFAULT_VIRTUAL_STEPS = 100;
    public static final int STREAM_MUSIC = 3;  // AudioManager.STREAM_MUSIC

    private List<Stage> stages = new ArrayList<>();
    private int virtualSteps = DEFAULT_VIRTUAL_STEPS;
    private int targetStream = STREAM_MUSIC;
    private boolean showSystemUi = true;

    public VolumeMapping() {
        // 默认三档：0-30 倍率0.6、30-60 倍率0.8、60-100 倍率2.0
        stages.add(new Stage(0, 30, 0.6f));
        stages.add(new Stage(30, 60, 0.8f));
        stages.add(new Stage(60, 100, 2.0f));
    }

    public List<Stage> getStages() {
        return stages;
    }

    public void setStages(List<Stage> stages) {
        this.stages = stages;
    }

    public int getVirtualSteps() {
        return virtualSteps;
    }

    public void setVirtualSteps(int virtualSteps) {
        this.virtualSteps = Math.max(2, Math.min(200, virtualSteps));
    }

    public int getTargetStream() {
        return targetStream;
    }

    public void setTargetStream(int targetStream) {
        this.targetStream = targetStream;
    }

    public boolean isShowSystemUi() {
        return showSystemUi;
    }

    public void setShowSystemUi(boolean showSystemUi) {
        this.showSystemUi = showSystemUi;
    }

    /**
     * 核心算法：把「虚拟档位 index」（0..virtualSteps-1）映射成「虚拟进度 0..100」，
     * 再按分段倍率换算成「请求进度 0..100」，最后按系统 maxVolume 量纲化。
     */
    public int mapToRealIndex(int virtualIndex, int systemMaxVolume) {
        float p = (float) virtualIndex / (virtualSteps - 1) * 100f; // 0..100
        float r = mapProgress(p);
        return Math.round(r / 100f * systemMaxVolume);
    }

    /**
     * 把虚拟进度 p 按分段倍率映射成请求进度 r。
     * 三段累计长度归一化到 100，倍率是该段相对「整段线性」的权重。
     */
    public float mapProgress(float p) {
        p = clamp(p, 0f, 100f);
        // 每段宽度
        float[] w = new float[stages.size()];
        for (int i = 0; i < stages.size(); i++) w[i] = stages.get(i).upper - stages.get(i).lower;
        // 加权总长度：各段宽度 × 倍率
        float totalWeighted = 0f;
        for (int i = 0; i < stages.size(); i++) totalWeighted += w[i] * stages.get(i).multiplier;
        if (totalWeighted <= 0f) return p;
        // 找到 p 落在哪一段
        for (int i = 0; i < stages.size(); i++) {
            Stage st = stages.get(i);
            boolean last = (i == stages.size() - 1);
            if (p <= st.upper || last) {
                float denom = (w[i] <= 0f) ? 1f : w[i];
                float t = clamp((p - st.lower) / denom, 0f, 1f); // 段内局部 0..1
                float before = 0f;
                for (int j = 0; j < i; j++) before += w[j] * stages.get(j).multiplier;
                float local = t * w[i] * st.multiplier;
                return (before + local) / totalWeighted * 100f;
            }
        }
        return p;
    }

    /**
     * 诊断：把整段映射表以「虚拟档位 -> 真实 index」的形式输出，便于核对曲线是否符合预期。
     */
    public String dumpCurve(int systemMaxVolume) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(virtualSteps, 25);
        for (int i = 0; i <= n; i++) {
            int v = Math.round((float) i / n * (virtualSteps - 1));
            int r = mapToRealIndex(v, systemMaxVolume);
            sb.append(v).append(" -> ").append(r).append("\n");
        }
        return sb.toString();
    }

    /** 反向映射：给定当前系统 index，估算它在虚拟档位上大概在哪。 */
    public int realIndexToVirtual(int realIndex, int systemMaxVolume) {
        float r = (float) realIndex / systemMaxVolume * 100f;
        // 二分找 p，使 mapProgress(p) ≈ r
        float lo = 0f, hi = 100f;
        for (int k = 0; k < 30; k++) {
            float mid = (lo + hi) / 2f;
            if (mapProgress(mid) < r) lo = mid;
            else hi = mid;
        }
        return Math.round(lo / 100f * (virtualSteps - 1));
    }

    private static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    /* ============================ 持久化 ============================ */

    private static final String PREFS = "fine_volume_prefs";
    private static final String KEY_JSON = "mapping_json";
    private static final String KEY_STEPS = "virtual_steps";
    private static final String KEY_STREAM = "target_stream";
    private static final String KEY_UI = "show_ui";

    public void save(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONObject root = new JSONObject();
        try {
            JSONArray arr = new JSONArray();
            for (Stage s : stages) arr.put(s.toJson());
            root.put("stages", arr);
            root.put("virtualSteps", virtualSteps);
            root.put("targetStream", targetStream);
            root.put("showUi", showSystemUi);
        } catch (JSONException ignored) {}
        sp.edit().putString(KEY_JSON, root.toString())
                .putInt(KEY_STEPS, virtualSteps)
                .putInt(KEY_STREAM, targetStream)
                .putBoolean(KEY_UI, showSystemUi)
                .apply();
    }

    public static VolumeMapping load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        VolumeMapping m = new VolumeMapping();
        String json = sp.getString(KEY_JSON, null);
        if (json != null) {
            try {
                JSONObject root = new JSONObject(json);
                JSONArray arr = root.getJSONArray("stages");
                List<Stage> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) list.add(Stage.fromJson(arr.getJSONObject(i)));
                if (!list.isEmpty()) m.setStages(list);
                m.setVirtualSteps(root.optInt("virtualSteps", DEFAULT_VIRTUAL_STEPS));
                m.setTargetStream(root.optInt("targetStream", STREAM_MUSIC));
                m.setShowSystemUi(root.optBoolean("showUi", true));
                return m;
            } catch (JSONException ignored) {}
        }
        // 兼容旧版本
        m.setVirtualSteps(sp.getInt(KEY_STEPS, DEFAULT_VIRTUAL_STEPS));
        m.setTargetStream(sp.getInt(KEY_STREAM, STREAM_MUSIC));
        m.setShowSystemUi(sp.getBoolean(KEY_UI, true));
        return m;
    }
}
