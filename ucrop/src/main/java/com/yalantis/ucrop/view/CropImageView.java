package com.yalantis.ucrop.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yalantis.ucrop.R;
import com.yalantis.ucrop.callback.BitmapCropCallback;
import com.yalantis.ucrop.callback.CropBoundsChangeListener;
import com.yalantis.ucrop.model.CropParameters;
import com.yalantis.ucrop.model.ImageState;
import com.yalantis.ucrop.task.BitmapCropTask;
import com.yalantis.ucrop.util.CubicEasing;
import com.yalantis.ucrop.util.RectUtils;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.Executors;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 * <p/>
 * This class adds crop feature, methods to draw crop guidelines, and keep image in correct state.
 * Also it extends parent class methods to add checks for scale; animating zoom in/out.
 * 第二层:
 * 图片裁剪框偏移计算、图片归位动画处理、裁剪图片
 * 绘制裁剪边框和网格
 * 为裁剪区域设置一张图片（如果用户对图片操作导致裁剪区域出现了空白，那么图片应自动移动到边界填充空白区域）
 * 继承父类方法，使用更精准的规则来操作矩阵（限制最大和最小缩放比）
 * 添加放大和缩小的方法
 * 裁剪图片
 * <p>
 * 这一层几乎囊括了所有的要对图片进行变换和裁剪的所有操作,但也仅仅是指明了做这些事情的方法，我们还需要支持手势
 */
public class CropImageView extends TransformImageView {

    public static final int DEFAULT_MAX_BITMAP_SIZE = 0;
    public static final int DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION = 500;
    public static final float DEFAULT_MAX_SCALE_MULTIPLIER = 10.0f;
    public static final float SOURCE_IMAGE_ASPECT_RATIO = 0f;
    public static final float DEFAULT_ASPECT_RATIO = SOURCE_IMAGE_ASPECT_RATIO;

    /**
     * 裁剪矩形位置
     */
    private final RectF mCropRect = new RectF();

    private final Matrix mTempMatrix = new Matrix();

    /**
     * 图片的宽高比
     */
    private float mTargetAspectRatio;
    /**
     * 最大缩放系数
     */
    private float mMaxScaleMultiplier = DEFAULT_MAX_SCALE_MULTIPLIER;

    private CropBoundsChangeListener mCropBoundsChangeListener;

    private Runnable mWrapCropBoundsRunnable, mZoomImageToPositionRunnable = null;

    /**
     * 最大/最新缩放值
     */
    private float mMaxScale, mMinScale;
    private int mMaxResultImageSizeX = 0, mMaxResultImageSizeY = 0;
    private long mImageToWrapCropBoundsAnimDuration = DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION;

    public CropImageView(Context context) {
        this(context, null);
    }

    public CropImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CropImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Cancels all current animations and sets image to fill crop area (without animation).
     * Then creates and executes {@link BitmapCropTask} with proper parameters.
     * 第三步：裁剪图片
     * <p>
     * 取消所有当前动画并设置图像以填充裁剪区域（不带动画）。
     * 然后用适当的参数创建并执行{@link BitmapCropTask}。
     */
    public void cropAndSaveImage(@NonNull Bitmap.CompressFormat compressFormat, int compressQuality,
                                 @Nullable BitmapCropCallback cropCallback) {
        //取消缩放和平移动画
        cancelAllAnimations();
        //设置要剪裁的图片,移动图片填充满裁剪区域，不需要位移动画
        setImageToWrapCropBounds(false);

        //存储图片信息，四个参数分别为：mCropRect用于剪裁的图片矩形，当前图片要剪裁的矩形，当前缩放比例值，当前旋转的角度
        final ImageState imageState = new ImageState(
                mCropRect, RectUtils.trapToRect(mCurrentImageCorners),
                getCurrentScale(), getCurrentAngle());

        //剪裁参数，mMaxResultImageSizeX，mMaxResultImageSizeY：剪裁图片的最大宽度、高度。
        final CropParameters cropParameters = new CropParameters(
                mMaxResultImageSizeX, mMaxResultImageSizeY,
                compressFormat, compressQuality,
                getImageInputPath(), getImageOutputPath(), getExifInfo());

        //剪裁操作放到AsyncTask中执行,将原图片,裁剪信息和约束参数传入
        new BitmapCropTask(getContext(), getViewBitmap(), imageState, cropParameters, cropCallback)
                .executeOnExecutor(Executors.newCachedThreadPool());
    }

    /**
     * @return - maximum scale value for current image and crop ratio
     */
    public float getMaxScale() {
        return mMaxScale;
    }

    /**
     * @return - minimum scale value for current image and crop ratio
     */
    public float getMinScale() {
        return mMinScale;
    }

    /**
     * @return - aspect ratio for crop bounds
     */
    public float getTargetAspectRatio() {
        return mTargetAspectRatio;
    }

    /**
     * Updates current crop rectangle with given. Also recalculates image properties and position
     * to fit new crop rectangle.
     * <p>
     * 设置裁剪矩形,裁剪框变化会被调用
     *
     * @param cropRect - new crop rectangle
     */
    public void setCropRect(RectF cropRect) {
        //根据裁剪矩形计算宽高比
        mTargetAspectRatio = cropRect.width() / cropRect.height();

        //注意此处裁剪矩形考虑到了padding值
        mCropRect.set(cropRect.left - getPaddingLeft(), cropRect.top - getPaddingTop(),
                cropRect.right - getPaddingRight(), cropRect.bottom - getPaddingBottom());

        //重新计算图片最小和最大缩放比例
        calculateImageScaleBounds();
        //填充满空白区域
        setImageToWrapCropBounds();
    }

    /**
     * This method sets aspect ratio for crop bounds.
     * If {@link #SOURCE_IMAGE_ASPECT_RATIO} value is passed - aspect ratio is calculated
     * based on current image width and height.
     * <p>
     * 设置裁剪图片宽高比,如果值为0,则使用图片原始宽高比
     *
     * @param targetAspectRatio - aspect ratio for image crop (e.g. 1.77(7) for 16:9)
     */
    public void setTargetAspectRatio(float targetAspectRatio) {
        final Drawable drawable = getDrawable();
        if (drawable == null) {
            mTargetAspectRatio = targetAspectRatio;
            return;
        }

        if (targetAspectRatio == SOURCE_IMAGE_ASPECT_RATIO) {
            mTargetAspectRatio = drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight();
        } else {
            mTargetAspectRatio = targetAspectRatio;
        }

        if (mCropBoundsChangeListener != null) {
            mCropBoundsChangeListener.onCropAspectRatioChanged(mTargetAspectRatio);
        }
    }

    @Nullable
    public CropBoundsChangeListener getCropBoundsChangeListener() {
        return mCropBoundsChangeListener;
    }

    public void setCropBoundsChangeListener(@Nullable CropBoundsChangeListener cropBoundsChangeListener) {
        mCropBoundsChangeListener = cropBoundsChangeListener;
    }

    /**
     * This method sets maximum width for resulting cropped image
     * 设置裁剪图片的最大宽度
     *
     * @param maxResultImageSizeX - size in pixels
     */
    public void setMaxResultImageSizeX(@IntRange(from = 10) int maxResultImageSizeX) {
        mMaxResultImageSizeX = maxResultImageSizeX;
    }

    /**
     * This method sets maximum width for resulting cropped image
     * 设置裁剪图片的最大高度
     *
     * @param maxResultImageSizeY - size in pixels
     */
    public void setMaxResultImageSizeY(@IntRange(from = 10) int maxResultImageSizeY) {
        mMaxResultImageSizeY = maxResultImageSizeY;
    }

    /**
     * This method sets animation duration for image to wrap the crop bounds
     * 图片填充空白动画的持续时间
     *
     * @param imageToWrapCropBoundsAnimDuration - duration in milliseconds
     */
    public void setImageToWrapCropBoundsAnimDuration(@IntRange(from = 100) long imageToWrapCropBoundsAnimDuration) {
        if (imageToWrapCropBoundsAnimDuration > 0) {
            mImageToWrapCropBoundsAnimDuration = imageToWrapCropBoundsAnimDuration;
        } else {
            throw new IllegalArgumentException("Animation duration cannot be negative value.");
        }
    }

    /**
     * This method sets multiplier that is used to calculate max image scale from min image scale.
     * 设置通过最小缩放比例计算最大缩放比例的乘子
     *
     * @param maxScaleMultiplier - (minScale * maxScaleMultiplier) = maxScale
     */
    public void setMaxScaleMultiplier(float maxScaleMultiplier) {
        mMaxScaleMultiplier = maxScaleMultiplier;
    }

    /**
     * This method scales image down for given value related to image center.
     * 缩小图片
     */
    public void zoomOutImage(float deltaScale) {
        zoomOutImage(deltaScale, mCropRect.centerX(), mCropRect.centerY());
    }

    /**
     * This method scales image down for given value related given coords (x, y).
     */
    public void zoomOutImage(float scale, float centerX, float centerY) {
        if (scale >= getMinScale()) {
            postScale(scale / getCurrentScale(), centerX, centerY);
        }
    }

    /**
     * This method scales image up for given value related to image center.
     * 放大图片
     */
    public void zoomInImage(float deltaScale) {
        zoomInImage(deltaScale, mCropRect.centerX(), mCropRect.centerY());
    }

    /**
     * This method scales image up for given value related to given coords (x, y).
     */
    public void zoomInImage(float scale, float centerX, float centerY) {
        if (scale <= getMaxScale()) {
            postScale(scale / getCurrentScale(), centerX, centerY);
        }
    }

    /**
     * This method changes image scale for given value related to point (px, py) but only if
     * resulting scale is in min/max bounds.
     * <p>
     * 在最小和最大缩放值范围内,缩放图片
     *
     * @param deltaScale - scale value 缩放比例
     * @param px         - scale center X 缩放中心点x
     * @param py         - scale center Y 缩放中心点y
     */
    public void postScale(float deltaScale, float px, float py) {
        if (deltaScale > 1 && getCurrentScale() * deltaScale <= getMaxScale()) {
            super.postScale(deltaScale, px, py);
        } else if (deltaScale < 1 && getCurrentScale() * deltaScale >= getMinScale()) {
            super.postScale(deltaScale, px, py);
        }
    }

    /**
     * This method rotates image for given angle related to the image center.
     *
     * @param deltaAngle - angle to rotate
     */
    public void postRotate(float deltaAngle) {
        postRotate(deltaAngle, mCropRect.centerX(), mCropRect.centerY());
    }

    /**
     * This method cancels all current Runnable objects that represent animations.
     */
    public void cancelAllAnimations() {
        removeCallbacks(mWrapCropBoundsRunnable);
        removeCallbacks(mZoomImageToPositionRunnable);
    }

    /**
     * 图片填满空白区域
     */
    public void setImageToWrapCropBounds() {
        setImageToWrapCropBounds(true);
    }

    /**
     * If image doesn't fill the crop bounds it must be translated and scaled properly to fill those.
     * <p/>
     * Therefore this method calculates delta X, Y and scale values and passes them to the
     * {@link WrapCropBoundsRunnable} which animates image.
     * Scale value must be calculated only if image won't fill the crop bounds after it's translated to the
     * crop bounds rectangle center. Using temporary variables this method checks this case.
     * 第一步：图片裁剪框偏移计算
     * 当用户手指移开时，要确保图片处于裁剪区域中，如果不处于，需要通过平移把它移过来,如果平移后还有空白,则缩放
     * 如果需要计算缩放比例,则需要将裁剪矩形旋转到和图片平行状态,然后才能计算缩放比例
     * <p>
     * 确定在裁剪范围内没有空白区域
     * 计算出所有要做的转换，使得图片可以返回到裁剪区域内
     * <p>
     * 转换图片让其包裹住裁剪区域
     */
    public void setImageToWrapCropBounds(boolean animate) {
        //如果图片加载完毕并且图片不处于剪裁区域内,也就是说裁剪区域超过了图片区域,有空白
        if (mBitmapLaidOut && !isImageWrapCropBounds()) {

            //获取图片中心点X,Y坐标
            float currentX = mCurrentImageCenter[0];
            float currentY = mCurrentImageCenter[1];

            //获取缩放比例
            float currentScale = getCurrentScale();

            //获取图片中心点到裁剪矩形中心点的偏移距离
            float deltaX = mCropRect.centerX() - currentX;
            float deltaY = mCropRect.centerY() - currentY;
            float deltaScale = 0;

            mTempMatrix.reset();
            mTempMatrix.setTranslate(deltaX, deltaY);

            //图片4个顶点的位置坐标
            final float[] tempCurrentImageCorners = Arrays.copyOf(mCurrentImageCorners, mCurrentImageCorners.length);
            //将当前图片的4个顶点坐标进行偏移
            mTempMatrix.mapPoints(tempCurrentImageCorners);

            //将当前图片平移到裁剪区域并检测图片是否填充满了,判断图片是否包含在剪裁区域
            boolean willImageWrapCropBoundsAfterTranslate = isImageWrapCropBounds(tempCurrentImageCorners);

            //如果偏移后,图片能包含在剪裁区域,就只进行偏移
            if (willImageWrapCropBoundsAfterTranslate) {
                //获取偏移的距离,左和上缩进为正,右和下缩进值为负
                final float[] imageIndents = calculateImageIndents();

                //偏移的距离,这里是图片到裁剪框的距离,由于是图片要向裁剪框移动,所以取反，横坐标加横坐标 纵坐标加纵坐标
                deltaX = -(imageIndents[0] + imageIndents[2]);
                deltaY = -(imageIndents[1] + imageIndents[3]);
            } else {
                //如果中心点偏移后不包含在剪裁区域,需要进行缩放
                //如果图片不能完全填充裁剪区域的话，那么矩阵的平移变换肯定是要伴随尺寸变换的

                //创建临时矩形,对临时矩阵进行旋转，然后将裁剪区域矩形映射到一个临时变量中
                RectF tempCropRect = new RectF(mCropRect);
                mTempMatrix.reset();
                //设置偏移角度
                mTempMatrix.setRotate(getCurrentAngle());
                //按图片的方向旋转裁剪矩形后,映射处理的坐标,旋转后矩形坐标点已经变换了,甚至矩形大小也变化了
                //相当于图片和裁剪矩形现在是完全平行的,平行之后才能计算缩放比例,这样计算出的缩放比例参考的坐标系才一致
                mTempMatrix.mapRect(tempCropRect);

                //获得图片矩形的边长,第一个值是宽,第二个值是高
                final float[] currentImageSides = RectUtils.getRectSidesFromCorners(mCurrentImageCorners);

                //获取放大比例
                deltaScale = Math.max(tempCropRect.width() / currentImageSides[0],
                        tempCropRect.height() / currentImageSides[1]);
                //缩放比例偏移量
                deltaScale = deltaScale * currentScale - currentScale;
            }

            //如果需要动画
            if (animate) {
                post(mWrapCropBoundsRunnable = new WrapCropBoundsRunnable(
                        CropImageView.this, mImageToWrapCropBoundsAnimDuration, currentX, currentY, deltaX, deltaY,
                        currentScale, deltaScale, willImageWrapCropBoundsAfterTranslate));
            } else {
                //不需要动画，直接移动到目标位置
                postTranslate(deltaX, deltaY);

                if (!willImageWrapCropBoundsAfterTranslate) {
                    zoomInImage(currentScale + deltaScale, mCropRect.centerX(), mCropRect.centerY());
                }
            }
        }
    }

    /**
     * First, un-rotate image and crop rectangles (make image rectangle axis-aligned).
     * Second, calculate deltas between those rectangles sides.
     * Third, depending on delta (its sign) put them or zero inside an array.
     * Fourth, using Matrix, rotate back those points (indents).
     * 计算图片的左上右下的缩进值,左和上缩进为正,右和下缩进值为负
     *
     * @return - the float array of image indents (4 floats) - in this order [left, top, right, bottom]
     */
    private float[] calculateImageIndents() {
        mTempMatrix.reset();
        mTempMatrix.setRotate(-getCurrentAngle());

        float[] unrotatedImageCorners = Arrays.copyOf(mCurrentImageCorners, mCurrentImageCorners.length);
        float[] unrotatedCropBoundsCorners = RectUtils.getCornersFromRect(mCropRect);

        //将图片和裁剪矩形的4个点按图片旋转的相反方向进行坐标点映射.也就是图片回正,裁剪矩形旋转
        mTempMatrix.mapPoints(unrotatedImageCorners);
        mTempMatrix.mapPoints(unrotatedCropBoundsCorners);

        //计算包含各个顶点对应的最小矩形
        RectF unrotatedImageRect = RectUtils.trapToRect(unrotatedImageCorners);
        RectF unrotatedCropRect = RectUtils.trapToRect(unrotatedCropBoundsCorners);

        //计算两个矩形上下左右距离
        float deltaLeft = unrotatedImageRect.left - unrotatedCropRect.left;
        float deltaTop = unrotatedImageRect.top - unrotatedCropRect.top;
        float deltaRight = unrotatedImageRect.right - unrotatedCropRect.right;
        float deltaBottom = unrotatedImageRect.bottom - unrotatedCropRect.bottom;

        //存储两个矩形上下左右的缩进值,为0表示已经包含了,不用额外处理了,否则还需要进行缩进
        float[] indents = new float[4];
        //deltaLeft > 0代表图片left>裁剪框left,此时需要向相反方向缩进
        indents[0] = (deltaLeft > 0) ? deltaLeft : 0;
        indents[1] = (deltaTop > 0) ? deltaTop : 0;
        indents[2] = (deltaRight < 0) ? deltaRight : 0;
        indents[3] = (deltaBottom < 0) ? deltaBottom : 0;

        mTempMatrix.reset();
        //重新旋转矩阵
        mTempMatrix.setRotate(getCurrentAngle());
        //由于计算缩进值得时候,矩阵是旋转了的,所以最后要将缩进值进行重新映射
        mTempMatrix.mapPoints(indents);

        return indents;
    }

    /**
     * When image is laid out it must be centered properly to fit current crop bounds.
     * 父类TransformImageView会回调
     * 图片布局完成后,根据图片信息计算一些默认值
     */
    @Override
    protected void onImageLaidOut() {
        super.onImageLaidOut();
        final Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }

        //图片的真实宽高
        float drawableWidth = drawable.getIntrinsicWidth();
        float drawableHeight = drawable.getIntrinsicHeight();

        //图片宽高比
        if (mTargetAspectRatio == SOURCE_IMAGE_ASPECT_RATIO) {
            //drawableWidth:1,drawableHeight:2,mTargetAspectRatio:0.5
            //drawableWidth:2,drawableHeight:1,mTargetAspectRatio:2
            mTargetAspectRatio = drawableWidth / drawableHeight;
        }

        //下面的代码,是让裁剪矩形在图片的正中间位置
        //根据组件的宽度和图片宽高比计算高度
        int height = (int) (mThisWidth / mTargetAspectRatio);
        //如果计算出来的高度大于组件高度
        if (height > mThisHeight) {
            int width = (int) (mThisHeight * mTargetAspectRatio);
            int halfDiff = (mThisWidth - width) / 2;
            mCropRect.set(halfDiff, 0, width + halfDiff, mThisHeight);
        } else {
            int halfDiff = (mThisHeight - height) / 2;
            mCropRect.set(0, halfDiff, mThisWidth, height + halfDiff);
        }

        //计算图片的最小和最大缩放比例
        calculateImageScaleBounds(drawableWidth, drawableHeight);
        //计算图片初始位置
        setupInitialImagePosition(drawableWidth, drawableHeight);

        if (mCropBoundsChangeListener != null) {
            mCropBoundsChangeListener.onCropAspectRatioChanged(mTargetAspectRatio);
        }

        if (mTransformImageListener != null) {
            mTransformImageListener.onScale(getCurrentScale());
            mTransformImageListener.onRotate(getCurrentAngle());
        }
    }

    /**
     * This method checks whether current image fills the crop bounds.
     * 检测裁剪区域内是否已经填充满了图片
     * 如何在一个XY对称的矩形内检测是否包含一个旋转过的矩形?
     * 只需要检测裁剪区域的四个顶点的坐标是不是都落在了图片区域内就可以了
     */
    protected boolean isImageWrapCropBounds() {
        return isImageWrapCropBounds(mCurrentImageCorners);
    }

    /**
     * This methods checks whether a rectangle that is represented as 4 corner points (8 floats)
     * fills the crop bounds rectangle.
     * <p>
     * 确定图片的4个角是否填充满了裁剪区域
     * 核心算法:
     * 如何在一个XY对称的矩形内检测是否包含一个旋转过的矩形
     * 将图片回正,然后将裁剪区域旋转图片的角度
     * 只需要检测裁剪区域的四个顶点的坐标是不是都落在了图片区域内就可以了
     * <p>
     * 就是在“正”的矩形内判断是否包含旋转的矩形。所以，将两个矩形同时进行反转，反转的角度就是图片区域旋转的角度
     *
     * @param imageCorners - corners of a rectangle
     * @return - true if it wraps crop bounds, false - otherwise
     */
    protected boolean isImageWrapCropBounds(float[] imageCorners) {
        //将矩阵重置为单位矩阵
        mTempMatrix.reset();
        //矩阵按照图片的相反方向进行旋转
        mTempMatrix.setRotate(-getCurrentAngle());

        //目前未旋转的图片4个顶点的坐标副本
        float[] unrotatedImageCorners = Arrays.copyOf(imageCorners, imageCorners.length);
        //将坐标点进行重新映射,映射后的结果就是图片回正的对应坐标点了,坐标点继续写回到输入参数数组中
        mTempMatrix.mapPoints(unrotatedImageCorners);

        //获取裁剪矩形对应的4个顶点
        float[] unrotatedCropBoundsCorners = RectUtils.getCornersFromRect(mCropRect);
        //将坐标点进行重新映射,映射后的结果就是裁剪矩形旋转后对应坐标点了
        mTempMatrix.mapPoints(unrotatedCropBoundsCorners);

        //判断两个矩形是否有包含关系
        return RectUtils.trapToRect(unrotatedImageCorners)
                .contains(RectUtils.trapToRect(unrotatedCropBoundsCorners));
    }

    /**
     * This method changes image scale (animating zoom for given duration), related to given center (x,y).
     *
     * @param scale      - target scale
     * @param centerX    - scale center X
     * @param centerY    - scale center Y
     * @param durationMs - zoom animation duration
     */
    protected void zoomImageToPosition(float scale, float centerX, float centerY, long durationMs) {
        if (scale > getMaxScale()) {
            scale = getMaxScale();
        }

        final float oldScale = getCurrentScale();
        final float deltaScale = scale - oldScale;

        post(mZoomImageToPositionRunnable = new ZoomImageToPosition(CropImageView.this,
                durationMs, oldScale, deltaScale, centerX, centerY));
    }

    /**
     * 计算图片的最小和最大缩放比例
     */
    private void calculateImageScaleBounds() {
        final Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }

        calculateImageScaleBounds(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }

    /**
     * This method calculates image minimum and maximum scale values for current {@link #mCropRect}.
     * 计算图片的最小和最大缩放比例
     *
     * @param drawableWidth  - image width 图片真实宽度
     * @param drawableHeight - image height 图片真实高度
     */
    private void calculateImageScaleBounds(float drawableWidth, float drawableHeight) {
        float widthScale = Math.min(mCropRect.width() / drawableWidth, mCropRect.width() / drawableHeight);
        float heightScale = Math.min(mCropRect.height() / drawableHeight, mCropRect.height() / drawableWidth);

        mMinScale = Math.min(widthScale, heightScale);
        mMaxScale = mMinScale * mMaxScaleMultiplier;
    }

    /**
     * This method calculates initial image position so it is positioned properly.
     * Then it sets those values to the current image matrix.
     * <p>
     * 计算图片初始位置
     *
     * @param drawableWidth  - image width 图片真实宽度
     * @param drawableHeight - image height 图片真实高度
     */
    private void setupInitialImagePosition(float drawableWidth, float drawableHeight) {
        float cropRectWidth = mCropRect.width();
        float cropRectHeight = mCropRect.height();

        //初始裁剪矩形肯定小于等于图片大小,所以下面两个值是小于1的
        float widthScale = mCropRect.width() / drawableWidth;
        float heightScale = mCropRect.height() / drawableHeight;

        //这里取最大值,保证图片不要缩放的太小,至少要保证图片缩放后,仍然包含裁剪矩形
        float initialMinScale = Math.max(widthScale, heightScale);

        //将图片向裁剪矩形中心移动
        float tw = (cropRectWidth - drawableWidth * initialMinScale) / 2.0f + mCropRect.left;
        float th = (cropRectHeight - drawableHeight * initialMinScale) / 2.0f + mCropRect.top;

        mCurrentImageMatrix.reset();
        mCurrentImageMatrix.postScale(initialMinScale, initialMinScale);
        mCurrentImageMatrix.postTranslate(tw, th);
        //设置矩阵值,会重新计算图片4个顶点和中心点的位置,并且会进行重新布局
        setImageMatrix(mCurrentImageMatrix);
    }

    /**
     * This method extracts all needed values from the styled attributes.
     * Those are used to configure the view.
     * <p>
     * 提取属性值
     */
    @SuppressWarnings("deprecation")
    protected void processStyledAttributes(@NonNull TypedArray a) {
        float targetAspectRatioX = Math.abs(a.getFloat(R.styleable.ucrop_UCropView_ucrop_aspect_ratio_x, DEFAULT_ASPECT_RATIO));
        float targetAspectRatioY = Math.abs(a.getFloat(R.styleable.ucrop_UCropView_ucrop_aspect_ratio_y, DEFAULT_ASPECT_RATIO));

        if (targetAspectRatioX == SOURCE_IMAGE_ASPECT_RATIO || targetAspectRatioY == SOURCE_IMAGE_ASPECT_RATIO) {
            mTargetAspectRatio = SOURCE_IMAGE_ASPECT_RATIO;
        } else {
            //设置默认缩放比例
            mTargetAspectRatio = targetAspectRatioX / targetAspectRatioY;
        }
    }

    /**
     * This Runnable is used to animate an image so it fills the crop bounds entirely.
     * Given values are interpolated during the animation time.
     * Runnable can be terminated either vie {@link #cancelAllAnimations()} method
     * or when certain conditions inside {@link WrapCropBoundsRunnable#run()} method are triggered.
     * <p>
     * 第二步：处理平移
     * 通过一个Runnable线程来处理平移，并且通过时间差值的计算来移动动画，使动画看起来更真实
     * <p>
     * 此可运行文件用于动画图像，使其完全填充裁剪边界。
     * 给定值在动画期间内插。
     * runnable可以终止于view{@link #cancelAllAnimations()}方法，
     * 也可以在触发{@link WrapCropBoundsRunnable#run()}方法内的某些条件时终止。
     */
    private static class WrapCropBoundsRunnable implements Runnable {

        private final WeakReference<CropImageView> mCropImageView;

        /**
         * 动画持续时间和开始时间
         */
        private final long mDurationMs, mStartTime;
        /**
         * 图片原来的中心点坐标
         */
        private final float mOldX, mOldY;
        /**
         * 图片中心点到裁剪矩形中心点的偏移距离
         */
        private final float mCenterDiffX, mCenterDiffY;
        /**
         * 当前缩放比例
         */
        private final float mOldScale;
        /**
         * 缩放比例偏移量
         */
        private final float mDeltaScale;
        /**
         * 将当前图片平移到裁剪区域并检测图片是否填充满了,判断图片是否包含在剪裁区域
         */
        private final boolean mWillBeImageInBoundsAfterTranslate;

        public WrapCropBoundsRunnable(CropImageView cropImageView,
                                      long durationMs,
                                      float oldX, float oldY,
                                      float centerDiffX, float centerDiffY,
                                      float oldScale, float deltaScale,
                                      boolean willBeImageInBoundsAfterTranslate) {

            mCropImageView = new WeakReference<>(cropImageView);

            mDurationMs = durationMs;
            mStartTime = System.currentTimeMillis();
            mOldX = oldX;
            mOldY = oldY;
            mCenterDiffX = centerDiffX;
            mCenterDiffY = centerDiffY;
            mOldScale = oldScale;
            mDeltaScale = deltaScale;
            mWillBeImageInBoundsAfterTranslate = willBeImageInBoundsAfterTranslate;
        }

        @Override
        public void run() {
            CropImageView cropImageView = mCropImageView.get();
            if (cropImageView == null) {
                return;
            }

            long now = System.currentTimeMillis();
            float currentMs = Math.min(mDurationMs, now - mStartTime);

            //使用插值函数计算出当前动画时刻对应的值
            float newX = CubicEasing.easeOut(currentMs, 0, mCenterDiffX, mDurationMs);
            float newY = CubicEasing.easeOut(currentMs, 0, mCenterDiffY, mDurationMs);
            float newScale = CubicEasing.easeInOut(currentMs, 0, mDeltaScale, mDurationMs);

            //动画还没有结束
            if (currentMs < mDurationMs) {
                //先进行平移,平移后,图片的4个顶点和中心点坐标会改变
                cropImageView.postTranslate(newX - (cropImageView.mCurrentImageCenter[0] - mOldX),
                        newY - (cropImageView.mCurrentImageCenter[1] - mOldY));

                //如果平移后,无法填满裁剪区域,说明图片需要进行缩放
                if (!mWillBeImageInBoundsAfterTranslate) {
                    cropImageView.zoomInImage(mOldScale + newScale,
                            cropImageView.mCropRect.centerX(), cropImageView.mCropRect.centerY());
                }

                //如果还是有空白区域,则再次执行run方法,从而产生动画效果
                if (!cropImageView.isImageWrapCropBounds()) {
                    cropImageView.post(this);
                }
            }
        }
    }

    /**
     * This Runnable is used to animate an image zoom.
     * Given values are interpolated during the animation time.
     * Runnable can be terminated either vie {@link #cancelAllAnimations()} method
     * or when certain conditions inside {@link ZoomImageToPosition#run()} method are triggered.
     * <p>
     * 此可运行项用于设置图像缩放的动画。
     * 给定值在动画期间内插。
     * runnable可以终止view {@link #cancelAllAnimations()}方法，
     * 也可以在触发{@link ZoomImageToPosition#run()}方法内的某些条件时终止。
     */
    private static class ZoomImageToPosition implements Runnable {

        private final WeakReference<CropImageView> mCropImageView;

        private final long mDurationMs, mStartTime;
        private final float mOldScale;
        private final float mDeltaScale;
        /**
         * 缩放的中心点坐标
         */
        private final float mDestX;
        private final float mDestY;

        public ZoomImageToPosition(CropImageView cropImageView,
                                   long durationMs,
                                   float oldScale, float deltaScale,
                                   float destX, float destY) {

            mCropImageView = new WeakReference<>(cropImageView);

            mStartTime = System.currentTimeMillis();
            mDurationMs = durationMs;
            mOldScale = oldScale;
            mDeltaScale = deltaScale;
            mDestX = destX;
            mDestY = destY;
        }

        @Override
        public void run() {
            CropImageView cropImageView = mCropImageView.get();
            if (cropImageView == null) {
                return;
            }

            long now = System.currentTimeMillis();
            float currentMs = Math.min(mDurationMs, now - mStartTime);
            float newScale = CubicEasing.easeInOut(currentMs, 0, mDeltaScale, mDurationMs);

            if (currentMs < mDurationMs) {
                cropImageView.zoomInImage(mOldScale + newScale, mDestX, mDestY);
                cropImageView.post(this);
            } else {
                cropImageView.setImageToWrapCropBounds();
            }
        }

    }

}
