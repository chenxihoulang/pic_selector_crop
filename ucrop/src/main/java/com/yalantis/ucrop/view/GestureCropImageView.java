package com.yalantis.ucrop.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.yalantis.ucrop.util.RotationGestureDetector;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 * 第三层：
 * 监听用户手势，并调用对应的正确的方法
 * 这个类主要就是对手势的监听
 * <p>
 * 1.缩放操作
 * 双击放大
 * 两只手指的拉伸
 * <p>
 * 2.滑动操作
 * 通过手指的拖动来拖拽图片
 * <p>
 * 3.旋转操作
 * 用户将两个手指放在图片上，做出旋转操作便可以对图片进行旋转
 * 重要的是，上面的所有操作是可以同时执行的，所有的图形变换必须被应用于用户手指之间的焦点，
 * 这样用户就会觉得是他在屏幕上拖动图片。
 */
public class GestureCropImageView extends CropImageView {

    private static final int DOUBLE_TAP_ZOOM_DURATION = 200;

    private ScaleGestureDetector mScaleDetector;
    private RotationGestureDetector mRotateDetector;
    private GestureDetector mGestureDetector;

    /**
     * 缩放手势的两个手指中心点位置
     */
    private float mMidPntX, mMidPntY;

    private boolean mIsRotateEnabled = true, mIsScaleEnabled = true;
    /**
     * 从最小比例到最大比例可以通过5次tap完成
     */
    private int mDoubleTapScaleSteps = 5;

    public GestureCropImageView(Context context) {
        super(context);
    }

    public GestureCropImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureCropImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setScaleEnabled(boolean scaleEnabled) {
        mIsScaleEnabled = scaleEnabled;
    }

    public boolean isScaleEnabled() {
        return mIsScaleEnabled;
    }

    public void setRotateEnabled(boolean rotateEnabled) {
        mIsRotateEnabled = rotateEnabled;
    }

    public boolean isRotateEnabled() {
        return mIsRotateEnabled;
    }

    public void setDoubleTapScaleSteps(int doubleTapScaleSteps) {
        mDoubleTapScaleSteps = doubleTapScaleSteps;
    }

    public int getDoubleTapScaleSteps() {
        return mDoubleTapScaleSteps;
    }

    /**
     * If it's ACTION_DOWN event - user touches the screen and all current animation must be canceled.
     * If it's ACTION_UP event - user removed all fingers from the screen and current image position must be corrected.
     * If there are more than 2 fingers - update focal point coordinates.
     * Pass the event to the gesture detectors if those are enabled.
     * <p>
     * 如果是ACTION_DOWN event，用户触摸屏幕，必须取消所有当前动画。
     * 如果是ACTION_UP event，用户从屏幕上取下所有手指，必须纠正当前图像位置。
     * 如果有两个以上的手指-更新焦点坐标。
     * 如果已启用，则将事件传递给手势检测器。
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            cancelAllAnimations();
        }

        //如果有多个手指,记录下前两个手指中间的坐标点
        if (event.getPointerCount() > 1) {
            mMidPntX = (event.getX(0) + event.getX(1)) / 2;
            mMidPntY = (event.getY(0) + event.getY(1)) / 2;
        }

        //双击监听和拖动监听
        mGestureDetector.onTouchEvent(event);

        //两指缩放监听
        if (mIsScaleEnabled) {
            mScaleDetector.onTouchEvent(event);
        }

        //旋转监听
        if (mIsRotateEnabled) {
            mRotateDetector.onTouchEvent(event);
        }

        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            //最后一指抬起时判断图片是否填充剪裁框
            setImageToWrapCropBounds();
        }
        return true;
    }

    @Override
    protected void init() {
        super.init();
        setupGestureListeners();
    }

    /**
     * This method calculates target scale value for double tap gesture.
     * User is able to zoom the image from min scale value
     * to the max scale value with {@link #mDoubleTapScaleSteps} double taps.
     * <p>
     * 获取当前双击图片需缩放的比例
     */
    protected float getDoubleTapTargetScale() {
        return getCurrentScale() * (float) Math.pow(getMaxScale() / getMinScale(), 1.0f / mDoubleTapScaleSteps);
    }

    /**
     * 初始化手势监听器
     */
    private void setupGestureListeners() {
        mGestureDetector = new GestureDetector(getContext(), new GestureListener(), null, true);
        mScaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        mRotateDetector = new RotationGestureDetector(new RotateListener());
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            postScale(detector.getScaleFactor(), mMidPntX, mMidPntY);
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            zoomImageToPosition(getDoubleTapTargetScale(), e.getX(), e.getY(), DOUBLE_TAP_ZOOM_DURATION);
            return super.onDoubleTap(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            postTranslate(-distanceX, -distanceY);
            return true;
        }

    }

    private class RotateListener extends RotationGestureDetector.SimpleOnRotationGestureListener {

        @Override
        public boolean onRotation(RotationGestureDetector rotationDetector) {
            postRotate(rotationDetector.getAngle(), mMidPntX, mMidPntY);
            return true;
        }

    }

}
