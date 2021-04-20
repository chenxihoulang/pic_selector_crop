package com.yalantis.ucrop.view.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.yalantis.ucrop.R;
import com.yalantis.ucrop.model.AspectRatio;
import com.yalantis.ucrop.view.CropImageView;

import java.util.Locale;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 * 图片缩放比例组件,可进行比例值宽高切换
 */
public class AspectRatioTextView extends AppCompatTextView {

    private final float MARGIN_MULTIPLIER = 1.5f;
    private final Rect mCanvasClipBounds = new Rect();
    private Paint mDotPaint;
    private int mDotSize;
    private float mAspectRatio;

    private String mAspectRatioTitle;
    private float mAspectRatioX, mAspectRatioY;

    public AspectRatioTextView(Context context) {
        this(context, null);
    }

    public AspectRatioTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AspectRatioTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ucrop_AspectRatioTextView);
        init(a);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AspectRatioTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ucrop_AspectRatioTextView);
        init(a);
    }

    /**
     * @param activeColor the resolved color for active elements
     *                    设置激活时的文本颜色
     */
    public void setActiveColor(@ColorInt int activeColor) {
        applyActiveColor(activeColor);
        invalidate();
    }

    /**
     * 设置显示的比例值或固定文本
     *
     * @param aspectRatio
     */
    public void setAspectRatio(@NonNull AspectRatio aspectRatio) {
        mAspectRatioTitle = aspectRatio.getAspectRatioTitle();
        mAspectRatioX = aspectRatio.getAspectRatioX();
        mAspectRatioY = aspectRatio.getAspectRatioY();

        if (mAspectRatioX == CropImageView.SOURCE_IMAGE_ASPECT_RATIO
                || mAspectRatioY == CropImageView.SOURCE_IMAGE_ASPECT_RATIO) {
            mAspectRatio = CropImageView.SOURCE_IMAGE_ASPECT_RATIO;
        } else {
            mAspectRatio = mAspectRatioX / mAspectRatioY;
        }

        setTitle();
    }

    public float getAspectRatio(boolean toggleRatio) {
        if (toggleRatio) {
            toggleAspectRatio();
            setTitle();
        }
        return mAspectRatio;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //此处调用了super,所以会进行正常的文本绘制和字体颜色改变
        super.onDraw(canvas);

        //如果选中,则绘制一个小圆点,使用的时候,该组件高度必须设置
        if (isSelected()) {
            //获取绘制文本的边界矩形区域
            canvas.getClipBounds(mCanvasClipBounds);

            float x = (mCanvasClipBounds.right - mCanvasClipBounds.left) / 2.0f;
            float y = (mCanvasClipBounds.bottom - mCanvasClipBounds.top / 2f) - mDotSize * MARGIN_MULTIPLIER;

            canvas.drawCircle(x, y, mDotSize / 2f, mDotPaint);
        }
    }

    @SuppressWarnings("deprecation")
    private void init(@NonNull TypedArray a) {
        setGravity(Gravity.CENTER_HORIZONTAL);

        mAspectRatioTitle = a.getString(R.styleable.ucrop_AspectRatioTextView_ucrop_artv_ratio_title);
        mAspectRatioX = a.getFloat(R.styleable.ucrop_AspectRatioTextView_ucrop_artv_ratio_x, CropImageView.SOURCE_IMAGE_ASPECT_RATIO);
        mAspectRatioY = a.getFloat(R.styleable.ucrop_AspectRatioTextView_ucrop_artv_ratio_y, CropImageView.SOURCE_IMAGE_ASPECT_RATIO);

        if (mAspectRatioX == CropImageView.SOURCE_IMAGE_ASPECT_RATIO
                || mAspectRatioY == CropImageView.SOURCE_IMAGE_ASPECT_RATIO) {
            mAspectRatio = CropImageView.SOURCE_IMAGE_ASPECT_RATIO;
        } else {
            mAspectRatio = mAspectRatioX / mAspectRatioY;
        }

        mDotSize = getContext().getResources().getDimensionPixelSize(R.dimen.ucrop_size_dot_scale_text_view);
        mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDotPaint.setStyle(Paint.Style.FILL);

        setTitle();

        int activeColor = getResources().getColor(R.color.ucrop_color_widget_active);
        applyActiveColor(activeColor);

        a.recycle();
    }

    /**
     * 设置选中或未选中时的状态文本颜色
     */
    private void applyActiveColor(@ColorInt int activeColor) {
        if (mDotPaint != null) {
            mDotPaint.setColor(activeColor);
        }
        ColorStateList textViewColorStateList = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_selected},
                        new int[]{0}
                },
                new int[]{
                        activeColor,
                        ContextCompat.getColor(getContext(), R.color.ucrop_color_widget)
                }
        );

        //根据状态设置文本颜色
        setTextColor(textViewColorStateList);
    }

    /**
     * 对调宽高比
     */
    private void toggleAspectRatio() {
        if (mAspectRatio != CropImageView.SOURCE_IMAGE_ASPECT_RATIO) {
            float tempRatioW = mAspectRatioX;
            mAspectRatioX = mAspectRatioY;
            mAspectRatioY = tempRatioW;

            mAspectRatio = mAspectRatioX / mAspectRatioY;
        }
    }

    private void setTitle() {
        if (!TextUtils.isEmpty(mAspectRatioTitle)) {
            setText(mAspectRatioTitle);
        } else {
            setText(String.format(Locale.CHINESE, "%d:%d", (int) mAspectRatioX, (int) mAspectRatioY));
        }
    }

}
