package com.luck.pictureselector;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * @author ChaiHongwei
 * @date 2020-10-28 13:10
 */
class AxisView extends View {
    private static final String TAG = AxisView.class.getSimpleName();

    public AxisView(Context context) {
        super(context);
    }

    public AxisView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AxisView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Log.e(TAG, "width:" + w);
        Log.e(TAG, "height:" + h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        CanvasAidUtils.draw2DCoordinateSpace(canvas);
    }
}
