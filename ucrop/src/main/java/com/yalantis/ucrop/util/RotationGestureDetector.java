package com.yalantis.ucrop.util;

import android.view.MotionEvent;

import androidx.annotation.NonNull;

/**
 * 旋转手势监听器
 */
public class RotationGestureDetector {

    private static final int INVALID_POINTER_INDEX = -1;

    /**
     * 第一个手指和第二个手指的x,y坐标
     */
    private float fX, fY, sX, sY;
    /**
     * 第一个手指和第二个手指在时间中的索引
     */
    private int mPointerIndex1, mPointerIndex2;

    private float mAngle;
    private boolean mIsFirstTouch;

    private OnRotationGestureListener mListener;

    public RotationGestureDetector(OnRotationGestureListener listener) {
        mListener = listener;
        mPointerIndex1 = INVALID_POINTER_INDEX;
        mPointerIndex2 = INVALID_POINTER_INDEX;
    }

    public float getAngle() {
        return mAngle;
    }

    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            //第一个手指按下
            case MotionEvent.ACTION_DOWN:
                sX = event.getX();
                sY = event.getY();
                mPointerIndex1 = event.findPointerIndex(event.getPointerId(0));
                mAngle = 0;
                mIsFirstTouch = true;
                break;
            //非第一个手指按下,此处主要指第二个手指按下
            case MotionEvent.ACTION_POINTER_DOWN:
                fX = event.getX();
                fY = event.getY();
                mPointerIndex2 = event.findPointerIndex(event.getPointerId(event.getActionIndex()));
                mAngle = 0;
                mIsFirstTouch = true;
                break;
            case MotionEvent.ACTION_MOVE:
                //保证至少有两个手指
                if (mPointerIndex1 != INVALID_POINTER_INDEX
                        && mPointerIndex2 != INVALID_POINTER_INDEX
                        && event.getPointerCount() > mPointerIndex2) {
                    float nfX, nfY, nsX, nsY;

                    nsX = event.getX(mPointerIndex1);
                    nsY = event.getY(mPointerIndex1);
                    nfX = event.getX(mPointerIndex2);
                    nfY = event.getY(mPointerIndex2);

                    //有新手指按下,旋转角度重置为0
                    if (mIsFirstTouch) {
                        mAngle = 0;
                        mIsFirstTouch = false;
                    } else {
                        calculateAngleBetweenLines(fX, fY, sX, sY, nfX, nfY, nsX, nsY);
                    }

                    if (mListener != null) {
                        mListener.onRotation(this);
                    }

                    sX = nsX;
                    sY = nsY;
                    fX = nfX;
                    fY = nfY;
                }
                break;
            //最后一个手指抬起时
            case MotionEvent.ACTION_UP:
                mPointerIndex1 = INVALID_POINTER_INDEX;
                break;
            //多个手指,有一个手指抬起
            case MotionEvent.ACTION_POINTER_UP:
                mPointerIndex2 = INVALID_POINTER_INDEX;
                break;
        }
        return true;
    }

    /**
     * 计算两条线之间的夹角
     */
    private float calculateAngleBetweenLines(float fx1, float fy1, float fx2, float fy2,
                                             float sx1, float sy1, float sx2, float sy2) {
        //余弦公式:向量1点乘向量2/(向量1模长*向量2模长)
        //(fx2-fx1,fy2-fy1)*(sx2-sx1,sy2-sy1)/(向量1模长*向量2模长)
//        float v1DotMulV2 = (fx2 - fx1) * (sx2 - sx1) + (fy2 - fy1) * (sy2 - sy1);
//        double v1Norm = Math.sqrt(Math.pow((fx2 - fx1), 2) + Math.pow((fy2 - fy1), 2));
//        double v2Norm = Math.sqrt(Math.pow((sx2 - sx1), 2) + Math.pow((sy2 - sy1), 2));
//        double cosAngle = v1DotMulV2 / (v1Norm * v2Norm);
//        double angle = Math.toDegrees(Math.acos(cosAngle));

        return calculateAngleDelta(
                (float) Math.toDegrees((float) Math.atan2((fy1 - fy2), (fx1 - fx2))),
                (float) Math.toDegrees((float) Math.atan2((sy1 - sy2), (sx1 - sx2))));
    }

    private float calculateAngleDelta(float angleFrom, float angleTo) {
        mAngle = angleTo % 360.0f - angleFrom % 360.0f;

        if (mAngle < -180.0f) {
            mAngle += 360.0f;
        } else if (mAngle > 180.0f) {
            mAngle -= 360.0f;
        }

        return mAngle;
    }

    public static class SimpleOnRotationGestureListener implements OnRotationGestureListener {

        @Override
        public boolean onRotation(RotationGestureDetector rotationDetector) {
            return false;
        }
    }

    public interface OnRotationGestureListener {

        boolean onRotation(RotationGestureDetector rotationDetector);
    }

}