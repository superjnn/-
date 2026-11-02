package com.finevolume.app;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private VolumeMapping mapping;
    private MappingView mappingView;
    private LinearLayout stageContainer;
    private EditText etVirtualSteps;
    private RadioGroup rgStream;
    private android.widget.CheckBox cbShowUi;
    private TextView tvStatus;

    private final View[] stageViews = new View[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapping = VolumeMapping.load(this);

        mappingView = findViewById(R.id.mappingView);
        stageContainer = findViewById(R.id.stageContainer);
        etVirtualSteps = findViewById(R.id.etVirtualSteps);
        rgStream = findViewById(R.id.rgStream);
        cbShowUi = findViewById(R.id.cbShowUi);
        tvStatus = findViewById(R.id.tvStatus);

        buildStageRows();
        bindValues();

        etVirtualSteps.addTextChangedListener(simpleWatcher(this::refresh));
        cbShowUi.setOnCheckedChangeListener((b, c) -> mapping.setShowSystemUi(c));

        rgStream.setOnCheckedChangeListener((g, id) -> {
            int stream;
            if (id == R.id.rbRing) stream = AudioManager.STREAM_RING;
            else if (id == R.id.rbAlarm) stream = AudioManager.STREAM_ALARM;
            else if (id == R.id.rbSystem) stream = AudioManager.STREAM_SYSTEM;
            else stream = AudioManager.STREAM_MUSIC;
            mapping.setTargetStream(stream);
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            if (collectAndValidate()) {
                mapping.save(this);
                ensureServiceRunning();
                Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnTestUp).setOnClickListener(v -> step(true));
        findViewById(R.id.btnTestDown).setOnClickListener(v -> step(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvStatus.setText(VolumeInterceptorService.isRunning()
                ? R.string.status_on : R.string.status_off);
    }

    private void buildStageRows() {
        LayoutInflater inf = LayoutInflater.from(this);
        List<VolumeMapping.Stage> stages = mapping.getStages();
        for (int i = 0; i < stages.size(); i++) {
            View row = inf.inflate(R.layout.item_stage, stageContainer, false);
            ((TextView) row.findViewById(R.id.tvStageLabel)).setText("第 " + (i + 1) + " 段");
            stageContainer.addView(row);
            stageViews[i] = row;
            final int idx = i;
            row.findViewById(R.id.etUpper).addTextChangedListener(simpleWatcher(() -> onStageChange(idx)));
            row.findViewById(R.id.etMultiplier).addTextChangedListener(simpleWatcher(() -> onStageChange(idx)));
        }
    }

    private void bindValues() {
        List<VolumeMapping.Stage> stages = mapping.getStages();
        for (int i = 0; i < stages.size(); i++) {
            VolumeMapping.Stage s = stages.get(i);
            ((EditText) stageViews[i].findViewById(R.id.etUpper)).setText(String.valueOf(s.upper));
            ((EditText) stageViews[i].findViewById(R.id.etMultiplier)).setText(String.valueOf(s.multiplier));
        }
        etVirtualSteps.setText(String.valueOf(mapping.getVirtualSteps()));
        cbShowUi.setChecked(mapping.isShowSystemUi());
        switch (mapping.getTargetStream()) {
            case AudioManager.STREAM_RING: rgStream.check(R.id.rbRing); break;
            case AudioManager.STREAM_ALARM: rgStream.check(R.id.rbAlarm); break;
            case AudioManager.STREAM_SYSTEM: rgStream.check(R.id.rbSystem); break;
            default: rgStream.check(R.id.rbMusic);
        }
        mappingView.setMapping(mapping);
    }

    private void onStageChange(int idx) {
        View row = stageViews[idx];
        String u = ((EditText) row.findViewById(R.id.etUpper)).getText().toString();
        String m = ((EditText) row.findViewById(R.id.etMultiplier)).getText().toString();
        if (!u.isEmpty() && !m.isEmpty()) {
            try {
                VolumeMapping.Stage s = mapping.getStages().get(idx);
                s.upper = Integer.parseInt(u);
                s.multiplier = Float.parseFloat(m);
                // 保证区间首尾衔接
                if (idx > 0) s.lower = mapping.getStages().get(idx - 1).upper;
                if (idx < mapping.getStages().size() - 1)
                    mapping.getStages().get(idx + 1).lower = s.upper;
                refresh();
            } catch (NumberFormatException ignored) {}
        }
    }

    private void refresh() {
        String vs = etVirtualSteps.getText().toString();
        if (!vs.isEmpty()) mapping.setVirtualSteps(Integer.parseInt(vs));
        mappingView.setMapping(mapping);
    }

    private boolean collectAndValidate() {
        try {
            mapping.setVirtualSteps(Integer.parseInt(etVirtualSteps.getText().toString()));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "虚拟档位数无效", Toast.LENGTH_SHORT).show();
            return false;
        }
        List<VolumeMapping.Stage> stages = mapping.getStages();
        for (int i = 0; i < stages.size(); i++) {
            String u = ((EditText) stageViews[i].findViewById(R.id.etUpper)).getText().toString();
            String m = ((EditText) stageViews[i].findViewById(R.id.etMultiplier)).getText().toString();
            if (u.isEmpty() || m.isEmpty()) {
                Toast.makeText(this, "第 " + (i + 1) + " 段填写不完整", Toast.LENGTH_SHORT).show();
                return false;
            }
            stages.get(i).upper = Integer.parseInt(u);
            stages.get(i).multiplier = Float.parseFloat(m);
            if (i > 0) stages.get(i).lower = stages.get(i - 1).upper;
        }
        if (stages.get(0).upper <= 0 || stages.get(stages.size() - 1).upper != 100) {
            Toast.makeText(this, "第一段上限需 >0，最后一段上限必须为 100", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void step(boolean up) {
        if (!collectAndValidate()) return;
        mapping.save(this);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am.isVolumeFixed()) {
            Toast.makeText(this, R.string.toast_fixed_volume, Toast.LENGTH_LONG).show();
            return;
        }
        int stream = mapping.getTargetStream();
        int max = am.getStreamMaxVolume(stream);
        int current = am.getStreamVolume(stream);
        int virtual = mapping.realIndexToVirtual(current, max);
        virtual += up ? 1 : -1;
        virtual = Math.max(0, Math.min(mapping.getVirtualSteps() - 1, virtual));
        int target = mapping.mapToRealIndex(virtual, max);
        target = Math.max(0, Math.min(max, target));
        am.setStreamVolume(stream, target, mapping.isShowSystemUi() ? AudioManager.FLAG_SHOW_UI : 0);
    }

    private void ensureServiceRunning() {
        // 无障碍服务需要用户在系统设置里手动开启，无法由代码直接启用。
        // 这里只是跳转到设置页，并提示用户。
        if (!VolumeInterceptorService.isRunning()) {
            Toast.makeText(this, R.string.toast_perm_required, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    private TextWatcher simpleWatcher(Runnable r) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { r.run(); }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        };
    }
}
