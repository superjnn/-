package com.finevolume.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * 把 VolumeMapping 的映射曲线画成折线图：
 *   横轴 = 虚拟进度 0..100
 *   纵轴 = 请求进度 mapProgress(p)
 * 用于让用户直观看到「哪些段被压缩、哪些段被拉伸」。
 */
public class MappingView extends View {

    private VolumeMapping mapping;
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public MappingView(Context context) {
        super(context);
        init();
    }

    public MappingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        axisPaint.setColor(Color.parseColor("#999999"));
        axisPaint.setStrokeWidth(2f);

        linePaint.setColor(Color.parseColor("#3F51B5"));
        linePaint.setStrokeWidth(4f);
        linePaint.setStyle(Paint.Style.STROKE);

        fillPaint.setColor(Color.parseColor("#3F51B5"));
        fillPaint.setAlpha(40);

        textPaint.setColor(Color.parseColor("#666666"));
        textPaint.setTextSize(28f);
    }

    public void setMapping(VolumeMapping m) {
        this.mapping = m;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        int pad = 40;
        int plotW = w - pad * 2;
        int plotH = h - pad * 2;

        // 坐标轴
        canvas.drawLine(pad, pad, pad, h - pad, axisPaint);
        canvas.drawLine(pad, h - pad, w - pad, h - pad, axisPaint);
        canvas.drawText("0", pad - 30, h - pad + 30, textPaint);
        canvas.drawText("100", w - pad - 40, h - pad + 30, textPaint);
        canvas.drawText("100", pad - 40, pad + 10, textPaint);

        if (mapping == null) return;

        path.reset();
        int steps = 200;
        for (int i = 0; i <= steps; i++) {
            float p = 100f * i / steps;
            float r = mapping.mapProgress(p);
            float x = pad + p / 100f * plotW;
            float y = (h - pad) - r / 100f * plotH;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        // 闭合填色
        Path fill = new Path(path);
        fill.lineTo(pad + plotW, h - pad);
        fill.lineTo(pad, h - pad);
        fill.close();
        canvas.drawPath(fill, fillPaint);
        canvas.drawPath(path, linePaint);

        // 分段分割线
        Paint div = new Paint(Paint.ANTI_ALIAS_FLAG);
        div.setColor(Color.parseColor("#FF4081"));
        div.setStrokeWidth(2f);
        div.setAlpha(160);
        for (int i = 0; i < mapping.getStages().size() - 1; i++) {
            float x = pad + mapping.getStages().get(i).upper / 100f * plotW;
            canvas.drawLine(x, pad, x, h - pad, div);
        }
    }
}
