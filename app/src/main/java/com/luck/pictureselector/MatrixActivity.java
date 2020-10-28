package com.luck.pictureselector;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Camera;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import java.util.Arrays;

/**
 * 参考:https://github.com/GcsSloop/AndroidNote
 */
public class MatrixActivity extends AppCompatActivity {
    private static final String TAG = MatrixActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matrix);

        ImageView ivRotateImg = findViewById(R.id.ivRotateImg);
        ivRotateImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 计算中心点（这里是使用view的中心作为旋转的中心点）
                final float centerX = v.getWidth() / 2.0f;
                final float centerY = v.getHeight() / 2.0f;

                //括号内参数分别为（上下文，开始角度，结束角度，x轴中心点，y轴中心点，深度，是否扭曲）
                final Rotate3dAnimation rotation = new Rotate3dAnimation(MatrixActivity.this,
                        0, 180, centerX, centerY, 0f, true);

                rotation.setDuration(3000);                         //设置动画时长
                rotation.setFillAfter(true);                        //保持旋转后效果
                rotation.setInterpolator(new LinearInterpolator());    //设置插值器
                v.startAnimation(rotation);
            }
        });

        ImageView ivMatrixImg = findViewById(R.id.ivMatrixImg);
        ivMatrixImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final SimpleCustomAnimation animation = new SimpleCustomAnimation();

                animation.setDuration(3000);                         //设置动画时长
                animation.setFillAfter(true);                        //保持旋转后效果
                animation.setInterpolator(new LinearInterpolator());    //设置插值器
                v.startAnimation(animation);
            }
        });


        /**
         * 错误结论一：pre 是顺序执行，post 是逆序执行。
         *即原始矩阵不为单位矩阵的时候，两者无法化简为相同的公式，结果自然也会不同。
         * 另外，执行顺序就是程序书写顺序，不存在所谓的正序逆序
         *
         * 由于上面例子中原始矩阵(M)是一个单位矩阵(I)，所以可得：
         *
         * // 第一段 pre
         * M' = (M*T)*R = I*T*R = T*R
         *
         * // 第二段 post
         * M' = T*(R*M) = T*R*I = T*R
         */

        // 第一段 pre  顺序执行，先平移(T)后旋转(R)
        Matrix matrix = new Matrix();
        matrix.preTranslate(100, 100);
        matrix.preRotate(45);
        Log.e("Matrix", matrix.toShortString());

        // 第二段 post 逆序执行，先平移(T)后旋转(R)
        Matrix matrix1 = new Matrix();
        matrix1.postRotate(45);
        matrix1.postTranslate(100, 100);
        Log.e("Matrix", matrix1.toShortString());


        /**
         * 错误结论二：pre 是先执行，而 post 是后执行。
         * pre 和 post 不能影响程序执行顺序，而程序每执行一条语句都会得出一个确定的结果，
         * 所以，它根本不能控制先后执行，属于完全扯淡型。
         *
         * 如果非要用这套理论强行解释的话，反而看起来像是 post 先执行，例如：
         *
         * // 矩阵乘法满足结合律
         * M‘ = T*(M*R) = T*M*R = (T*M)*R
         */
        Matrix matrix2 = new Matrix();
        matrix2.preRotate(45);
        matrix2.postTranslate(100, 100);


        /**
         * 当 T*S 的时候，缩放比例则不会影响到 MTRANS_X 和 MTRANS_Y
         */

        /**
         * 有两条基本定理：
         *
         * 所有的操作(旋转、平移、缩放、错切)默认都是以坐标原点为基准点的。
         *
         * 之前操作的坐标系状态会保留，并且影响到后续状态。
         *
         * 基于这两条基本定理，我们可以推算出要基于某一个点进行旋转需要如下步骤：
         *
         * 1. 先将坐标系原点移动到指定位置，使用平移 T
         * 2. 对坐标系进行旋转，使用旋转 S (围绕原点旋转)
         * 3. 再将坐标系平移回原来位置，使用平移 -T
         *
         * M' = M*T*R*-T = T*R*-T
         */

        /**
         * Matrix matrix = new Matrix();
         * matrix.preTranslate(pivotX,pivotY);
         * // 各种操作，旋转，缩放，错切等，可以执行多次。
         * matrix.preTranslate(-pivotX, -pivotY);
         *
         * M' = M*T* ... *-T = T* ... *-T
         */

        /**
         * Matrix matrix = new Matrix();
         * // 各种操作，旋转，缩放，错切等，可以执行多次。
         * matrix.postTranslate(pivotX,pivotY);
         * matrix.preTranslate(-pivotX, -pivotY);
         *
         * M' = T*M* ... *-T = T* ... *-T
         */

        // 使用pre， M' = M*T*S = T*S
        Matrix m = new Matrix();
        m.preTranslate(100, 100);
        m.preScale(1.2F, 1.2F);
        Log.e("Matrix1", m.toShortString());

        // 使用post， M‘ = T*S*M = T*S
        Matrix m1 = new Matrix();
        m1.postScale(1.2F, 1.2F);  //越靠前越先执行。
        m1.postTranslate(100, 100);
        Log.e("Matrix1", m1.toShortString());


        // 混合 M‘ = T*M*S = T*S
        Matrix m2 = new Matrix();
        m2.reset();
        m2.preScale(1.2F, 1.2F);
        m2.postTranslate(100, 100);
        Log.e("Matrix2", m2.toShortString());

        // 混合 M‘ = T*M*S = T*S
        Matrix m3 = new Matrix();
        m3.reset();
        m3.postTranslate(100, 100);
        m3.preScale(1.2F, 1.2F);
        Log.e("Matrix2", m3.toShortString());

        // 混合 M‘ = S*M*T=S*T
        Matrix m4 = new Matrix();
        m4.reset();
        m4.preTranslate(100, 100);
        m4.postScale(1.2F, 1.2F);
        Log.e("Matrix2", m4.toShortString());


        // 初始数据为三个点 (0, 0) (80, 100) (400, 300)
        float[] pts = new float[]{0, 0, 80, 100, 400, 300};

        // 构造一个matrix，x坐标缩放0.5
        Matrix matrix3 = new Matrix();
        matrix3.setScale(0.5f, 1f);

        // 输出pts计算之前数据
        Log.e(TAG, "before: " + Arrays.toString(pts));

        // 调用map方法计算
        matrix3.mapPoints(pts);

        // 输出pts计算之后数据
        Log.e(TAG, "after : " + Arrays.toString(pts));


//        // 初始数据为三个点 (0, 0) (80, 100) (400, 300)
//        float[] src = new float[]{0, 0, 80, 100, 400, 300};
//        float[] dst = new float[6];
//
//        // 构造一个matrix，x坐标缩放0.5
//        Matrix matrix4 = new Matrix();
//        matrix4.setScale(0.5f, 1f);
//
//        // 输出计算之前数据
//        Log.e(TAG, "before: src=" + Arrays.toString(src));
//        Log.e(TAG, "before: dst=" + Arrays.toString(dst));
//
//        // 调用map方法计算
//        matrix4.mapPoints(dst, src);
//
//        // 输出计算之后数据
//        Log.e(TAG, "after : src=" + Arrays.toString(src));
//        Log.e(TAG, "after : dst=" + Arrays.toString(dst));


//        // 初始数据为三个点 (0, 0) (80, 100) (400, 300)
//        float[] src = new float[]{0, 0, 80, 100, 400, 300};
//        float[] dst = new float[6];
//
//        // 构造一个matrix，x坐标缩放0.5
//        Matrix matrix5 = new Matrix();
//        matrix5.setScale(0.5f, 1f);
//
//        // 输出计算之前数据
//        Log.e(TAG, "before: src=" + Arrays.toString(src));
//        Log.e(TAG, "before: dst=" + Arrays.toString(dst));
//
//        // 调用map方法计算(最后一个2表示两个点，即四个数值,并非两个数值)
//        matrix5.mapPoints(dst, 0, src, 2, 2);
//
//        // 输出计算之后数据
//        Log.e(TAG, "after : src=" + Arrays.toString(src));
//        Log.e(TAG, "after : dst=" + Arrays.toString(dst));


        float radius = 100;
        float result = 0;

        // 构造一个matrix，x坐标缩放0.5
        Matrix matrix6 = new Matrix();
        matrix6.setScale(0.5f, 1f);

        Log.e(TAG, "mapRadius: " + radius);

        result = matrix6.mapRadius(radius);

        Log.e(TAG, "mapRadius: " + result);


        RectF rect = new RectF(400, 400, 1000, 800);
        // 构造一个matrix
        Matrix matrix7 = new Matrix();
        matrix7.setScale(0.5f, 1f);
        matrix7.postSkew(1, 0);

        Log.e(TAG, "mapRadius: " + rect.toString());

        boolean result1 = matrix7.mapRect(rect);

        Log.e(TAG, "mapRadius: " + rect.toString());
        Log.e(TAG, "isRect: " + result1);


        float[] src = new float[]{1000, 800};
        float[] dst = new float[2];

        // 构造一个matrix
        Matrix matrix8 = new Matrix();
        matrix8.setScale(0.5f, 1f);
        matrix8.postTranslate(100, 100);

        // 计算向量, 不受位移影响
        matrix8.mapVectors(dst, src);
        Log.e(TAG, "mapVectors: " + Arrays.toString(dst));

        // 计算点
        matrix8.mapPoints(dst, src);
        Log.e(TAG, "mapPoints: " + Arrays.toString(dst));


        Matrix matrix9 = new Matrix();
        // 旋转90度
        // sin90=1
        // cos90=0
        matrix9.setSinCos(1f, 0f);

        Log.e(TAG, "setSinCos:" + matrix9.toShortString());

        // 重置
        matrix9.reset();

        // 旋转90度
        matrix9.setRotate(90);

        Log.e(TAG, "setRotate:" + matrix9.toShortString());


        Matrix matrix10 = new Matrix();
        Matrix invert = new Matrix();
        matrix10.setTranslate(200, 500);

        Log.e(TAG, "before - matrix " + matrix10.toShortString());

        Boolean result2 = matrix10.invert(invert);

        Log.e(TAG, "after  - result " + result2);
        Log.e(TAG, "after  - matrix " + matrix10.toShortString());
        Log.e(TAG, "after  - invert " + invert.toShortString());


        Matrix matrix11 = new Matrix();
        Log.e(TAG, "isIdentity=" + matrix11.isIdentity());

        matrix11.postTranslate(200, 0);

        Log.e(TAG, "isIdentity=" + matrix11.isIdentity());


        //View的状态只取决于View和摄像机之间的相对位置，不过由于单位不同，摄像机平移一个单位等于View平移72个像素。
        Camera camera = new Camera();
        camera.setLocation(1, 0, -8);        // 摄像机默认位置是(0, 0, -8)
        Matrix matrix12 = new Matrix();
        camera.getMatrix(matrix12);
        Log.e(TAG, "location: " + matrix12.toShortString());

        Camera camera2 = new Camera();
        camera2.translate(-72, 0, 0);
        Matrix matrix13 = new Matrix();
        camera2.getMatrix(matrix13);
        Log.e(TAG, "translate: " + matrix13.toShortString());
    }
}