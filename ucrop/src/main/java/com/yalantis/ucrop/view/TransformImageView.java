package com.yalantis.ucrop.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.yalantis.ucrop.callback.BitmapLoadCallback;
import com.yalantis.ucrop.model.ExifInfo;
import com.yalantis.ucrop.util.BitmapLoadUtils;
import com.yalantis.ucrop.util.FastBitmapDrawable;
import com.yalantis.ucrop.util.RectUtils;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 * <p/>
 * This class provides base logic to setup the image, transform it with matrix (move, scale, rotate),
 * and methods to get current matrix state.
 * <p>
 * 第一层：
 * 从源拿到图片
 * 将图片进行变换（平移、缩放、旋转），并应用到当前图片上
 * 这一层并不知道裁剪或者手势等行为
 */
public class TransformImageView extends AppCompatImageView {

    private static final String TAG = "TransformImageView";

    private static final int RECT_CORNER_POINTS_COORDS = 8;
    private static final int RECT_CENTER_POINT_COORDS = 2;
    private static final int MATRIX_VALUES_COUNT = 9;

    /**
     * 当前图片4个角的位置,可能已经变换过
     */
    protected final float[] mCurrentImageCorners = new float[RECT_CORNER_POINTS_COORDS];
    /**
     * 当前图片中心点的位置,可能已经变换过
     */
    protected final float[] mCurrentImageCenter = new float[RECT_CENTER_POINT_COORDS];

    /**
     * 用于存储矩阵值
     */
    private final float[] mMatrixValues = new float[MATRIX_VALUES_COUNT];

    /**
     * 当前图片变换矩阵
     */
    protected Matrix mCurrentImageMatrix = new Matrix();
    /**
     * 组件的宽度和高度
     */
    protected int mThisWidth, mThisHeight;
    /**
     * 图片旋转缩放监听器
     */
    protected TransformImageListener mTransformImageListener;

    /**
     * 图片4个角的初始位置,布局完成后,不会变化,变化的只有矩阵
     */
    private float[] mInitialImageCorners;
    /**
     * 图片中心点初始位置,布局完成后,不会变化,变化的只有矩阵
     */
    private float[] mInitialImageCenter;

    /**
     * 图片是否完成解码,包括图片尺寸缩放和方向旋转
     */
    protected boolean mBitmapDecoded = false;
    /**
     * 图片是否布局完成,4个顶点和中心点坐标已经计算出来了
     */
    protected boolean mBitmapLaidOut = false;

    /**
     * 图片最大尺寸,默认为屏幕对角线长度
     */
    private int mMaxBitmapSize = 0;

    private String mImageInputPath, mImageOutputPath;
    /**
     * 图片元数据
     */
    private ExifInfo mExifInfo;

    /**
     * Interface for rotation and scale change notifying.
     */
    public interface TransformImageListener {

        void onLoadComplete();

        void onLoadFailure(@NonNull Exception e);

        void onRotate(float currentAngle);

        void onScale(float currentScale);

    }

    public TransformImageView(Context context) {
        this(context, null);
    }

    public TransformImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TransformImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void setTransformImageListener(TransformImageListener transformImageListener) {
        mTransformImageListener = transformImageListener;
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        if (scaleType == ScaleType.MATRIX) {
            super.setScaleType(scaleType);
        } else {
            Log.w(TAG, "Invalid ScaleType. Only ScaleType.MATRIX can be used");
        }
    }

    /**
     * Setter for {@link #mMaxBitmapSize} value.
     * Be sure to call it before {@link #setImageURI(Uri)} or other image setters.
     *
     * @param maxBitmapSize - max size for both width and height of bitmap that will be used in the view.
     */
    public void setMaxBitmapSize(int maxBitmapSize) {
        mMaxBitmapSize = maxBitmapSize;
    }

    /**
     * 这个方法计算bitmap的最大宽高,默认实现为设备屏幕对角线大小
     *
     * @return
     */
    public int getMaxBitmapSize() {
        if (mMaxBitmapSize <= 0) {
            mMaxBitmapSize = BitmapLoadUtils.calculateMaxBitmapSize(getContext());
        }
        return mMaxBitmapSize;
    }

    /**
     * 显示图片
     *
     * @param bitmap
     */
    @Override
    public void setImageBitmap(final Bitmap bitmap) {
        setImageDrawable(new FastBitmapDrawable(bitmap));
    }

    public String getImageInputPath() {
        return mImageInputPath;
    }

    public String getImageOutputPath() {
        return mImageOutputPath;
    }

    public ExifInfo getExifInfo() {
        return mExifInfo;
    }

    /**
     * This method takes an Uri as a parameter, then calls method to decode it into Bitmap with specified size.
     * 设置待裁剪的图片,这个方法会根据指定的大小进行图片解码到Bitmap中
     *
     * @param imageUri - image Uri
     * @throws Exception - can throw exception if having problems with decoding Uri or OOM.
     */
    public void setImageUri(@NonNull Uri imageUri, @Nullable Uri outputUri) throws Exception {
        //这个方法计算bitmap的最大宽高,默认实现为设备屏幕对角线大小
        int maxBitmapSize = getMaxBitmapSize();

        BitmapLoadUtils.decodeBitmapInBackground(getContext(), imageUri, outputUri, maxBitmapSize, maxBitmapSize,
                new BitmapLoadCallback() {

                    @Override
                    public void onBitmapLoaded(@NonNull Bitmap bitmap, @NonNull ExifInfo exifInfo,
                                               @NonNull String imageInputPath, @Nullable String imageOutputPath) {
                        mImageInputPath = imageInputPath;
                        mImageOutputPath = imageOutputPath;
                        mExifInfo = exifInfo;

                        //图片加载完成,包括图片尺寸缩放和方向旋转
                        mBitmapDecoded = true;

                        //展示图片,会触发onLayout
                        setImageBitmap(bitmap);
                    }

                    @Override
                    public void onFailure(@NonNull Exception bitmapWorkerException) {
                        Log.e(TAG, "onFailure: setImageUri", bitmapWorkerException);
                        if (mTransformImageListener != null) {
                            mTransformImageListener.onLoadFailure(bitmapWorkerException);
                        }
                    }
                });
    }

    /**
     * @return - current image scale value.
     * [1.0f - for original image, 2.0f - for 200% scaled image, etc.]
     */
    public float getCurrentScale() {
        return getMatrixScale(mCurrentImageMatrix);
    }

    /**
     * This method calculates scale value for given Matrix object.
     * 获取缩放值,我们这是等比例缩放,所以只需要获取x轴方向的缩放比例即可
     * 这里计算x方向的缩放比例,那么矩阵我们就只需要考虑对x能产生影响的两个值MSCALE_X和MSKEW_Y
     * 我们计算的缩放比例其实是一个三角形对角线的长度,因为缩放其实是改变坐标点位置,改变的可以任务是向量长度,
     * x1*scale=x2,那么这个scale是怎么计算的呢?
     * 想象一个边长为1的正方形通过中心点放大2倍,其实右上角坐标点向右和向上分别移动了1,
     * 初始右上角向量(点1,1)长度为根号2,放大2倍后的右上角向量(点2,2)长度为2*根号2
     * 那么移动的比例是怎么计算的呢?Math.sqrt(offsetX*offsetX+offsetY*offsetY)=根号2
     */
    public float getMatrixScale(@NonNull Matrix matrix) {
        return (float) Math.sqrt(Math.pow(getMatrixValue(matrix, Matrix.MSCALE_X), 2)
                + Math.pow(getMatrixValue(matrix, Matrix.MSKEW_Y), 2));
    }

    /**
     * @return - current image rotation angle.
     */
    public float getCurrentAngle() {
        return getMatrixAngle(mCurrentImageMatrix);
    }

    /**
     * This method calculates rotation angle for given Matrix object.
     * 获取旋转角度
     * 不明白为什么这样计算?
     */
    public float getMatrixAngle(@NonNull Matrix matrix) {
        return (float) -(Math.atan2(getMatrixValue(matrix, Matrix.MSKEW_X),
                getMatrixValue(matrix, Matrix.MSCALE_X)) * (180 / Math.PI));
    }

    @Override
    public void setImageMatrix(Matrix matrix) {
        super.setImageMatrix(matrix);
        mCurrentImageMatrix.set(matrix);
        updateCurrentImagePoints();
    }

    /**
     * 获取图片Bitmap
     */
    @Nullable
    public Bitmap getViewBitmap() {
        if (getDrawable() == null || !(getDrawable() instanceof FastBitmapDrawable)) {
            return null;
        } else {
            return ((FastBitmapDrawable) getDrawable()).getBitmap();
        }
    }

    /**
     * This method translates current image.
     *
     * @param deltaX - horizontal shift
     * @param deltaY - vertical shift
     */
    public void postTranslate(float deltaX, float deltaY) {
        if (deltaX != 0 || deltaY != 0) {
            mCurrentImageMatrix.postTranslate(deltaX, deltaY);
            setImageMatrix(mCurrentImageMatrix);
        }
    }

    /**
     * This method scales current image.
     *
     * @param deltaScale - scale value
     * @param px         - scale center X
     * @param py         - scale center Y
     */
    public void postScale(float deltaScale, float px, float py) {
        if (deltaScale != 0) {
            mCurrentImageMatrix.postScale(deltaScale, deltaScale, px, py);
            setImageMatrix(mCurrentImageMatrix);
            if (mTransformImageListener != null) {
                mTransformImageListener.onScale(getMatrixScale(mCurrentImageMatrix));
            }
        }
    }

    /**
     * This method rotates current image.
     *
     * @param deltaAngle - rotation angle
     * @param px         - rotation center X
     * @param py         - rotation center Y
     */
    public void postRotate(float deltaAngle, float px, float py) {
        if (deltaAngle != 0) {
            mCurrentImageMatrix.postRotate(deltaAngle, px, py);
            setImageMatrix(mCurrentImageMatrix);
            if (mTransformImageListener != null) {
                mTransformImageListener.onRotate(getMatrixAngle(mCurrentImageMatrix));
            }
        }
    }

    protected void init() {
        //设置图片通过矩阵进行缩放
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed || (mBitmapDecoded && !mBitmapLaidOut)) {

            left = getPaddingLeft();
            top = getPaddingTop();
            right = getWidth() - getPaddingRight();
            bottom = getHeight() - getPaddingBottom();

            //图片组件的宽高考虑到了填充
            mThisWidth = right - left;
            mThisHeight = bottom - top;

            onImageLaidOut();
        }
    }

    /**
     * When image is laid out {@link #mInitialImageCorners} and {@link #mInitialImageCenter}
     * must be set.
     *
     * 进行图片布局
     */
    protected void onImageLaidOut() {
        final Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }

        //图片的真实宽高
        float w = drawable.getIntrinsicWidth();
        float h = drawable.getIntrinsicHeight();

        Log.d(TAG, String.format("Image size: [%d:%d]", (int) w, (int) h));

        //真实图片的边框位置
        RectF initialImageRect = new RectF(0, 0, w, h);

        //4个角的位置
        mInitialImageCorners = RectUtils.getCornersFromRect(initialImageRect);
        //中心点的位置
        mInitialImageCenter = RectUtils.getCenterFromRect(initialImageRect);

        //图片布局完成
        mBitmapLaidOut = true;

        //图片布局完成后,才进行回调
        if (mTransformImageListener != null) {
            mTransformImageListener.onLoadComplete();
        }
    }

    /**
     * This method returns Matrix value for given index.
     * 获取指定索引处的矩阵值
     *
     * @param matrix     - valid Matrix object
     * @param valueIndex - index of needed value. See {@link Matrix#MSCALE_X} and others.
     * @return - matrix value for index
     */
    protected float getMatrixValue(@NonNull Matrix matrix,
                                   @IntRange(from = 0, to = MATRIX_VALUES_COUNT) int valueIndex) {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[valueIndex];
    }

    /**
     * This method logs given matrix X, Y, scale, and angle values.
     * Can be used for debug.
     * 打印矩阵信息,方便调试
     */
    @SuppressWarnings("unused")
    protected void printMatrix(@NonNull String logPrefix, @NonNull Matrix matrix) {
        float x = getMatrixValue(matrix, Matrix.MTRANS_X);
        float y = getMatrixValue(matrix, Matrix.MTRANS_Y);
        float rScale = getMatrixScale(matrix);
        float rAngle = getMatrixAngle(matrix);
        Log.d(TAG, logPrefix + ": matrix: { x: " + x + ", y: " + y + ", scale: " + rScale + ", angle: " + rAngle + " }");
    }

    /**
     * This method updates current image corners and center points that are stored in
     * {@link #mCurrentImageCorners} and {@link #mCurrentImageCenter} arrays.
     * Those are used for several calculations.
     * <p>
     * 更新图片4个角和中心点的位置
     * 每当图片矩阵发生变化时，都会更新图片的中心和角落坐标
     * 注意:这里图片初始的4个顶点和中心点一直没有变化,变化的只有当前图片矩阵
     */
    private void updateCurrentImagePoints() {
        mCurrentImageMatrix.mapPoints(mCurrentImageCorners, mInitialImageCorners);
        mCurrentImageMatrix.mapPoints(mCurrentImageCenter, mInitialImageCenter);
    }

}
