package com.luck.pictureselector;

import android.graphics.Matrix;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * 参考:https://www.jianshu.com/p/6aa6080373ab
 */
public class SimpleCustomAnimation extends Animation {

    private int mWidth, mHeight;

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        this.mWidth = width;
        this.mHeight = height;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        Matrix matrix = t.getMatrix();
        /**
         * 注意pre和post的区别:TMSRT,pre就是原来计算出来的矩阵在表达式的前面,post就是原来计算出来的矩阵在表达式的后面
         * 整个表达式一定是从前往后执行,并且缩放S,旋转R,平移T操作(旋转、平移、缩放、错切)默认都是以坐标原点为基准点的
         * TMSR-T:先平移T,在缩放S,在旋转R,再平移负T
         */
        matrix.preScale(interpolatedTime, interpolatedTime);//缩放
        matrix.preRotate(interpolatedTime * 360);//旋转

        //下面的Translate组合是为了将缩放和旋转的基点移动到整个View的中心，不然系统默认是以View的左上角作为基点
        matrix.postTranslate(mWidth / 2, mHeight / 2);
        matrix.preTranslate(-mWidth / 2, -mHeight / 2);
    }
}
