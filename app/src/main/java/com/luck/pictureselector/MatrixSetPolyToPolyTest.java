package com.luck.pictureselector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Arrays;

public class MatrixSetPolyToPolyTest extends View {
    private static final String TAG = MatrixSetPolyToPolyTest.class.getSimpleName();

    private Bitmap mBitmap;             // 要绘制的图片
    private Matrix mPolyMatrix;         // 测试setPolyToPoly用的Matrix

    public MatrixSetPolyToPolyTest(Context context) {
        super(context);

        initBitmapAndMatrix();
    }

    public MatrixSetPolyToPolyTest(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        initBitmapAndMatrix();
    }

    private void initBitmapAndMatrix() {
        mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

        mPolyMatrix = new Matrix();


        float[] src = {0, 0,                                    // 左上
                mBitmap.getWidth(), 0,                          // 右上
                mBitmap.getWidth(), mBitmap.getHeight(),        // 右下
                0, mBitmap.getHeight()};                        // 左下

        float[] dst = {0, 0,                                    // 左上
                mBitmap.getWidth(), 70,                         // 右上
                mBitmap.getWidth(), mBitmap.getHeight() - 20,   // 右下
                0, mBitmap.getHeight()};                        // 左下

        // 核心要点,应该与PS中自由变换中的扭曲有点类似
        //pointCount支持点的个数为0到4个，四个一般指图形的四个角
        mPolyMatrix.setPolyToPoly(src, 0, dst, 0, src.length >> 1); // src.length >> 1 为位移运算 相当于处以2

        // 此处为了更好的显示对图片进行了等比缩放和平移(图片本身有点大)
//        mPolyMatrix.postScale(0.26f, 0.26f);
        mPolyMatrix.postTranslate(0, 200);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 根据Matrix绘制一个变换后的图片
        //作用范围当然是设置了Matrix的全部区域，如果你将这个Matrix赋值给了Canvas，
        //它的作用范围就是整个画布，如果你赋值给了Bitmap，它的作用范围就是整张图片。
        canvas.drawBitmap(mBitmap, mPolyMatrix, null);


        float[] values = new float[9];
        int[] location1 = new int[2];

        Matrix matrix = canvas.getMatrix();
        matrix.getValues(values);

        location1[0] = (int) values[2];
        location1[1] = (int) values[5];
        Log.e(TAG, "location1 = " + Arrays.toString(location1));

        int[] location2 = new int[2];
        this.getLocationOnScreen(location2);
        Log.e(TAG, "location2 = " + Arrays.toString(location2));
    }
}