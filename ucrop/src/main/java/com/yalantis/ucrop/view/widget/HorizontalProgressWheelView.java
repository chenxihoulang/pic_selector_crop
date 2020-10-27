package com.yalantis.ucrop.view.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.yalantis.ucrop.R;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 * 水平刻度滚动滑轮组件
 */
public class HorizontalProgressWheelView extends View {

    private final Rect mCanvasClipBounds = new Rect();

    private ScrollingListener mScrollingListener;
    private float mLastTouchedPosition;

    private Paint mProgressLinePaint;
    private Paint mProgressMiddleLinePaint;
    private int mProgressLineWidth, mProgressLineHeight;
    private int mProgressLineMargin;

    /**
     * 是否开始滑动
     */
    private boolean mScrollStarted;
    /**
     * 滑动的总距离
     */
    private float mTotalScrollDistance;

    private int mMiddleLineColor;

    public HorizontalProgressWheelView(Context context) {
        this(context, null);
    }

    public HorizontalProgressWheelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HorizontalProgressWheelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public HorizontalProgressWheelView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setScrollingListener(ScrollingListener scrollingListener) {
        mScrollingListener = scrollingListener;
    }

    /**
     * 设置中间指示线的颜色
     *
     * @param middleLineColor
     */
    public void setMiddleLineColor(@ColorInt int middleLineColor) {
        mMiddleLineColor = middleLineColor;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastTouchedPosition = event.getX();
                break;
            case MotionEvent.ACTION_UP:
                //滑动结束
                if (mScrollingListener != null) {
                    mScrollStarted = false;
                    mScrollingListener.onScrollEnd();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float distance = event.getX() - mLastTouchedPosition;
                if (distance != 0) {
                    if (!mScrollStarted) {
                        mScrollStarted = true;
                        //滑动开始
                        if (mScrollingListener != null) {
                            mScrollingListener.onScrollStart();
                        }
                    }

                    //滑动中
                    onScrollEvent(event, distance);
                }
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //获取Canvas裁剪界限,获取组件可绘制区域的边界矩形
        canvas.getClipBounds(mCanvasClipBounds);

        //可绘制的线条总数
        int linesCount = mCanvasClipBounds.width() / (mProgressLineWidth + mProgressLineMargin);
        //其实我们滑动的时候,需要进行偏移的距离就只是线条宽度+线条之间的间距(区分正负),然后重新绘制所有线条就可以了
        float deltaX = (mTotalScrollDistance) % (float) (mProgressLineWidth + mProgressLineMargin);

        //绘制线条
        for (int i = 0; i < linesCount; i++) {
            //左边和右边1/4的线条颜色比较浅一点,中间1/2的颜色深一点,通过改变透明度来实现
            if (i < (linesCount / 4)) {
                mProgressLinePaint.setAlpha((int) (255 * (i / (float) (linesCount / 4))));
            } else if (i > (linesCount * 3 / 4)) {
                mProgressLinePaint.setAlpha((int) (255 * ((linesCount - i) / (float) (linesCount / 4))));
            } else {
                mProgressLinePaint.setAlpha(255);
            }

            /**
             * 绘制刻度线,因为绘制线条或者边框等绘制的时候,一般是通过画笔的StrokeWidth来做的,
             * 在绘制的时候是参照StrokeWidth/2的位置,所以线条的y坐标根据裁剪编辑中心+正负1/4的线条高度来动态计算会比较好
             */
            canvas.drawLine(
                    -deltaX + mCanvasClipBounds.left + i * (mProgressLineWidth + mProgressLineMargin),
                    mCanvasClipBounds.centerY() - mProgressLineHeight / 4.0f,
                    -deltaX + mCanvasClipBounds.left + i * (mProgressLineWidth + mProgressLineMargin),
                    mCanvasClipBounds.centerY() + mProgressLineHeight / 4.0f,
                    mProgressLinePaint);
        }

        //绘制中间指示线,中间线条的高度是普通线条的2倍
        canvas.drawLine(mCanvasClipBounds.centerX(), mCanvasClipBounds.centerY() - mProgressLineHeight / 2.0f,
                mCanvasClipBounds.centerX(), mCanvasClipBounds.centerY() + mProgressLineHeight / 2.0f,
                mProgressMiddleLinePaint);

    }

    /**
     * 滑动事件
     *
     * @param distance 滑动距离
     */
    private void onScrollEvent(MotionEvent event, float distance) {
        //累计滑动总距离,向右滑动距离减小,左滑增大
        mTotalScrollDistance -= distance;
        //然后进行重绘
        postInvalidate();

        mLastTouchedPosition = event.getX();

        if (mScrollingListener != null) {
            mScrollingListener.onScroll(-distance, mTotalScrollDistance);
        }
    }

    private void init() {
        mMiddleLineColor = ContextCompat.getColor(getContext(), R.color.ucrop_color_widget_rotate_mid_line);

        mProgressLineWidth = getContext().getResources().getDimensionPixelSize(R.dimen.ucrop_width_horizontal_wheel_progress_line);
        mProgressLineHeight = getContext().getResources().getDimensionPixelSize(R.dimen.ucrop_height_horizontal_wheel_progress_line);
        mProgressLineMargin = getContext().getResources().getDimensionPixelSize(R.dimen.ucrop_margin_horizontal_wheel_progress_line);

        mProgressLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mProgressLinePaint.setStyle(Paint.Style.STROKE);
        mProgressLinePaint.setStrokeWidth(mProgressLineWidth);
        mProgressLinePaint.setColor(getResources().getColor(R.color.ucrop_color_progress_wheel_line));

        mProgressMiddleLinePaint = new Paint(mProgressLinePaint);
        mProgressMiddleLinePaint.setColor(mMiddleLineColor);
        mProgressMiddleLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mProgressMiddleLinePaint.setStrokeWidth(getContext().getResources().getDimensionPixelSize(R.dimen.ucrop_width_middle_wheel_progress_line));
    }

    public interface ScrollingListener {

        void onScrollStart();

        void onScroll(float delta, float totalDistance);

        void onScrollEnd();
    }

}
