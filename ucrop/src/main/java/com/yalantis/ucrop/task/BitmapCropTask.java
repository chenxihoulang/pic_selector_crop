package com.yalantis.ucrop.task;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.yalantis.ucrop.callback.BitmapCropCallback;
import com.yalantis.ucrop.model.CropParameters;
import com.yalantis.ucrop.model.ImageState;
import com.yalantis.ucrop.util.BitmapLoadUtils;
import com.yalantis.ucrop.util.FileUtils;
import com.yalantis.ucrop.util.ImageHeaderParser;
import com.yalantis.ucrop.util.MimeType;
import com.yalantis.ucrop.util.SdkUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

/**
 * Crops part of image that fills the crop bounds.
 * <p/>
 * First image is downscaled if max size was set and if resulting image is larger that max size.
 * Then image is rotated accordingly.
 * Finally new Bitmap object is created and saved to file.
 * <p>
 * 在异步线程中执行裁剪图片
 */
public class BitmapCropTask extends AsyncTask<Void, Void, Throwable> {

    private static final String TAG = "BitmapCropTask";

    private final WeakReference<Context> mContextWeakReference;

    private Bitmap mViewBitmap;

    private final RectF mCropRect;
    private final RectF mCurrentImageRect;

    private float mCurrentScale, mCurrentAngle;
    private final int mMaxResultImageSizeX, mMaxResultImageSizeY;

    private final Bitmap.CompressFormat mCompressFormat;
    private final int mCompressQuality;
    private final String mImageInputPath, mImageOutputPath;
    private final BitmapCropCallback mCropCallback;

    private int mCroppedImageWidth, mCroppedImageHeight;
    private int cropOffsetX, cropOffsetY;

    public BitmapCropTask(@NonNull Context context, @Nullable Bitmap viewBitmap, @NonNull ImageState imageState, @NonNull CropParameters cropParameters,
                          @Nullable BitmapCropCallback cropCallback) {

        mContextWeakReference = new WeakReference<>(context);

        mViewBitmap = viewBitmap;
        mCropRect = imageState.getCropRect();
        mCurrentImageRect = imageState.getCurrentImageRect();

        mCurrentScale = imageState.getCurrentScale();
        mCurrentAngle = imageState.getCurrentAngle();
        mMaxResultImageSizeX = cropParameters.getMaxResultImageSizeX();
        mMaxResultImageSizeY = cropParameters.getMaxResultImageSizeY();

        mCompressFormat = cropParameters.getCompressFormat();
        mCompressQuality = cropParameters.getCompressQuality();

        mImageInputPath = cropParameters.getImageInputPath();
        mImageOutputPath = cropParameters.getImageOutputPath();

        mCropCallback = cropCallback;
    }

    private Context getContext() {
        return mContextWeakReference.get();
    }

    @Override
    @Nullable
    protected Throwable doInBackground(Void... params) {
        if (mViewBitmap == null) {
            return new NullPointerException("ViewBitmap is null");
        } else if (mViewBitmap.isRecycled()) {
            return new NullPointerException("ViewBitmap is recycled");
        } else if (mCurrentImageRect.isEmpty()) {
            return new NullPointerException("CurrentImageRect is empty");
        }

        try {
            crop();
            mViewBitmap = null;
        } catch (Throwable throwable) {
            return throwable;
        }

        return null;
    }

    /**
     * 调整剪裁大小，如果有设置最大剪裁大小也会在这里做调整到设置范围
     * 剪裁图片
     */
    private boolean crop() throws IOException {
        // Downsize if needed
        if (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0) {
            //计算当前裁剪框将要裁剪出的图片真实宽高,裁剪框看到的图片有可能是缩放后的图片
            //比如mCurrentScale=2,图片放大了两倍(假如大小等于两个屏幕大小),mCropRect为全屏的时候,只能裁剪出图片的一半
            float cropWidth = mCropRect.width() / mCurrentScale;
            float cropHeight = mCropRect.height() / mCurrentScale;

            //如果真实宽高大于期望的最大宽高,就需要进行缩放了
            if (cropWidth > mMaxResultImageSizeX || cropHeight > mMaxResultImageSizeY) {

                float scaleX = mMaxResultImageSizeX / cropWidth;
                float scaleY = mMaxResultImageSizeY / cropHeight;
                //根据最大裁剪图片尺寸进行缩放,注意这个值小于1
                float resizeScale = Math.min(scaleX, scaleY);

                //将原图片进行缩放
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(mViewBitmap,
                        Math.round(mViewBitmap.getWidth() * resizeScale),
                        Math.round(mViewBitmap.getHeight() * resizeScale), false);

                if (mViewBitmap != resizedBitmap) {
                    mViewBitmap.recycle();
                }
                //注意这里存储的是根据最大裁剪尺寸缩放后的图片了
                mViewBitmap = resizedBitmap;

                //原图缩放了,当前的缩放比例也要进行缩放,由于裁剪区域对应的图片较大,所以需要放大图片,这里是除以
                mCurrentScale /= resizeScale;
            }
        }

        // Rotate if needed
        //检查图片是否被旋转
        if (mCurrentAngle != 0) {
            Matrix tempMatrix = new Matrix();
            //根据图片中心点选中图片
            tempMatrix.setRotate(mCurrentAngle, mViewBitmap.getWidth() / 2, mViewBitmap.getHeight() / 2);

            //根据矩阵创建一个旋转后的图片
            Bitmap rotatedBitmap = Bitmap.createBitmap(mViewBitmap, 0, 0,
                    mViewBitmap.getWidth(), mViewBitmap.getHeight(), tempMatrix, true);

            if (mViewBitmap != rotatedBitmap) {
                mViewBitmap.recycle();
            }

            //注意这里存储的是旋转后的图片了
            mViewBitmap = rotatedBitmap;
        }

        //四舍五入取整
        //计算裁剪图片的左上角坐标
        cropOffsetX = Math.round((mCropRect.left - mCurrentImageRect.left) / mCurrentScale);
        cropOffsetY = Math.round((mCropRect.top - mCurrentImageRect.top) / mCurrentScale);
        //计算裁剪图片的宽高
        mCroppedImageWidth = Math.round(mCropRect.width() / mCurrentScale);
        mCroppedImageHeight = Math.round(mCropRect.height() / mCurrentScale);

        //计算出图片是否需要被剪裁
        boolean shouldCrop = shouldCrop(mCroppedImageWidth, mCroppedImageHeight);
        Log.i(TAG, "Should crop: " + shouldCrop);

        //需要裁剪
        if (shouldCrop) {
            /**
             *最核心方法:裁剪图片,就是在原图片上找一块区域,根据改区域对应的图片重新创建一个Bitmap
             */
            saveImage(Bitmap.createBitmap(mViewBitmap, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight));

            ParcelFileDescriptor parcelFileDescriptor = null;

            //剪裁成功复制图片EXIF信息
            if (mCompressFormat.equals(Bitmap.CompressFormat.JPEG)) {
                //获取图片原数据信息
                ExifInterface originalExif;
                if (SdkUtils.isQ() && MimeType.isContent(mImageInputPath)) {
                    parcelFileDescriptor =
                            getContext().getContentResolver().openFileDescriptor(Uri.parse(mImageInputPath), "r");
                    originalExif = new ExifInterface(new FileInputStream(parcelFileDescriptor.getFileDescriptor()));
                } else {
                    originalExif = new ExifInterface(mImageInputPath);
                }

                //拷贝图片源数据信息到新生成的图片上
                ImageHeaderParser.copyExif(originalExif, mCroppedImageWidth, mCroppedImageHeight, mImageOutputPath);
            }

            if (parcelFileDescriptor != null) {
                BitmapLoadUtils.close(parcelFileDescriptor);
            }

            return true;
        } else {
            //不需要裁剪,则直接复制图片到目标文件夹
            if (SdkUtils.isQ() && MimeType.isContent(mImageInputPath)) {
                ParcelFileDescriptor parcelFileDescriptor =
                        getContext().getContentResolver().openFileDescriptor(Uri.parse(mImageInputPath), "r");
                FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                FileUtils.copyFile(new FileInputStream(fileDescriptor), mImageOutputPath);
                BitmapLoadUtils.close(parcelFileDescriptor);
            } else {
                FileUtils.copyFile(mImageInputPath, mImageOutputPath);
            }
            return false;
        }
    }

    /**
     * 保存图片
     *
     * @param croppedBitmap
     * @throws FileNotFoundException
     */
    private void saveImage(@NonNull Bitmap croppedBitmap) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) {
            return;
        }

        OutputStream outputStream = null;
        try {
            outputStream = context.getContentResolver().openOutputStream(Uri.fromFile(new File(mImageOutputPath)));
            croppedBitmap.compress(mCompressFormat, mCompressQuality, outputStream);
            croppedBitmap.recycle();
        } finally {
            BitmapLoadUtils.close(outputStream);
        }
    }

    /**
     * Check whether an image should be cropped at all or just file can be copied to the destination path.
     * For each 1000 pixels there is one pixel of error due to matrix calculations etc.
     * 检查是否应完全裁剪图像或仅将文件复制到目标路径。
     * 由于矩阵计算等原因，每1000像素会有1像素的误差
     *
     * @param width  - crop area width 裁剪区域宽度
     * @param height - crop area height 裁剪区域高度
     * @return - true if image must be cropped, false - if original image fits requirements
     */
    private boolean shouldCrop(int width, int height) {
        int pixelError = 1;
        pixelError += Math.round(Math.max(width, height) / 1000f);

        return (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0)
                || Math.abs(mCropRect.left - mCurrentImageRect.left) > pixelError
                || Math.abs(mCropRect.top - mCurrentImageRect.top) > pixelError
                || Math.abs(mCropRect.bottom - mCurrentImageRect.bottom) > pixelError
                || Math.abs(mCropRect.right - mCurrentImageRect.right) > pixelError
                || mCurrentAngle != 0;
    }

    @Override
    protected void onPostExecute(@Nullable Throwable t) {
        if (mCropCallback != null) {
            if (t == null) {
                Uri uri = Uri.fromFile(new File(mImageOutputPath));
                mCropCallback.onBitmapCropped(uri, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight);
            } else {
                mCropCallback.onCropFailure(t);
            }
        }
    }

}
