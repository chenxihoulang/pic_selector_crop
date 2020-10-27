package com.yalantis.ucrop.task;

import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.yalantis.ucrop.callback.BitmapLoadCallback;
import com.yalantis.ucrop.model.ExifInfo;
import com.yalantis.ucrop.util.BitmapLoadUtils;
import com.yalantis.ucrop.util.FileUtils;
import com.yalantis.ucrop.util.MimeType;
import com.yalantis.ucrop.util.SdkUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.URL;

/**
 * Creates and returns a Bitmap for a given Uri(String url).
 * inSampleSize is calculated based on requiredWidth property. However can be adjusted if OOM occurs.
 * If any EXIF config is found - bitmap is transformed properly.
 * <p>
 * 这个类负责了Uri解码bitmap，并处理分辨率
 */
public class BitmapLoadTask extends AsyncTask<Void, Void, BitmapLoadTask.BitmapWorkerResult> {

    private static final String TAG = "BitmapWorkerTask";

    private WeakReference<Context> mContextWeakReference;
    private Uri mInputUri;
    private Uri mOutputUri;
    private final int mRequiredWidth;
    private final int mRequiredHeight;

    private final BitmapLoadCallback mBitmapLoadCallback;

    public static class BitmapWorkerResult {

        Bitmap mBitmapResult;
        ExifInfo mExifInfo;
        Exception mBitmapWorkerException;

        public BitmapWorkerResult(@NonNull Bitmap bitmapResult, @NonNull ExifInfo exifInfo) {
            mBitmapResult = bitmapResult;
            mExifInfo = exifInfo;
        }

        public BitmapWorkerResult(@NonNull Exception bitmapWorkerException) {
            mBitmapWorkerException = bitmapWorkerException;
        }

    }

    public BitmapLoadTask(@NonNull Context context,
                          @NonNull Uri inputUri, @Nullable Uri outputUri,
                          int requiredWidth, int requiredHeight,
                          BitmapLoadCallback loadCallback) {
        mContextWeakReference = new WeakReference<>(context);
        mInputUri = inputUri;
        mOutputUri = outputUri;
        mRequiredWidth = requiredWidth;
        mRequiredHeight = requiredHeight;
        mBitmapLoadCallback = loadCallback;
    }

    private Context getContext() {
        return mContextWeakReference.get();
    }

    @Override
    @NonNull
    protected BitmapWorkerResult doInBackground(Void... params) {
        if (mInputUri == null) {
            return new BitmapWorkerResult(new NullPointerException("Input Uri cannot be null"));
        }

        try {
            //加载图片,区分网络和本地图片,最终图片本地路径存放在mInputUri变量中
            processInputUri();
        } catch (NullPointerException | IOException e) {
            return new BitmapWorkerResult(e);
        }

        //根据拿到的Uri解析位图
        final ParcelFileDescriptor parcelFileDescriptor;
        try {
            //获取到文件描述符
            parcelFileDescriptor = getContext().getContentResolver().openFileDescriptor(mInputUri, "r");
        } catch (FileNotFoundException e) {
            return new BitmapWorkerResult(e);
        }

        final FileDescriptor fileDescriptor;
        if (parcelFileDescriptor != null) {
            fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        } else {
            return new BitmapWorkerResult(new NullPointerException("ParcelFileDescriptor was null for given Uri: [" + mInputUri + "]"));
        }

        //在解码之前，必须知道图片的大小，因为如果图片的分辨率太高的话，我们还是需要对其进行再次取样的
        final BitmapFactory.Options options = new BitmapFactory.Options();
        //只解析图片尺寸等信息
        options.inJustDecodeBounds = true;
        //通过文件描述符,获取到图片的尺寸等信息,存储到options变量中
        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
        if (options.outWidth == -1 || options.outHeight == -1) {
            return new BitmapWorkerResult(new IllegalArgumentException("Bounds for bitmap could not be retrieved from the Uri: [" + mInputUri + "]"));
        }

        //根据最大图片宽高,计算采样缩放比例
        options.inSampleSize = BitmapLoadUtils.calculateInSampleSize(options, mRequiredWidth, mRequiredHeight);
        //下面真正的开始解析图片了
        options.inJustDecodeBounds = false;

        Bitmap decodeSampledBitmap = null;

        boolean decodeAttemptSuccess = false;
        //循环解码,防止oom内存溢出
        while (!decodeAttemptSuccess) {
            try {
                decodeSampledBitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
                decodeAttemptSuccess = true;
            } catch (OutOfMemoryError error) {
                Log.e(TAG, "doInBackground: BitmapFactory.decodeFileDescriptor: ", error);
                options.inSampleSize *= 2;
            }
        }

        if (decodeSampledBitmap == null) {
            return new BitmapWorkerResult(new IllegalArgumentException("Bitmap could not be decoded from the Uri: [" + mInputUri + "]"));
        }

        //关闭文件描述符
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            BitmapLoadUtils.close(parcelFileDescriptor);
        }

        //解析图片格式的header数据,获取图片元数据信息
        int exifOrientation = BitmapLoadUtils.getExifOrientation(getContext(), mInputUri);
        //获取图片的角度
        int exifDegrees = BitmapLoadUtils.exifToDegrees(exifOrientation);
        int exifTranslation = BitmapLoadUtils.exifToTranslation(exifOrientation);

        ExifInfo exifInfo = new ExifInfo(exifOrientation, exifDegrees, exifTranslation);

        Matrix matrix = new Matrix();
        if (exifDegrees != 0) {
            matrix.preRotate(exifDegrees);
        }

        if (exifTranslation != 1) {
            matrix.postScale(exifTranslation, 1);
        }

        if (!matrix.isIdentity()) {
            return new BitmapWorkerResult(BitmapLoadUtils.transformBitmap(decodeSampledBitmap, matrix), exifInfo);
        }

        return new BitmapWorkerResult(decodeSampledBitmap, exifInfo);
    }

    /**
     * 加载图片,区分网络和本地图片
     */
    private void processInputUri() throws NullPointerException, IOException {
        String inputUriScheme = mInputUri.getScheme();
        Log.d(TAG, "Uri scheme: " + inputUriScheme);
        if ("http".equals(inputUriScheme) || "https".equals(inputUriScheme)) {
            try {
                //下载网络图片
                downloadFile(mInputUri, mOutputUri);
            } catch (NullPointerException | IOException e) {
                Log.e(TAG, "Downloading failed", e);
                throw e;
            }
        } else if ("content".equals(inputUriScheme)) {
            //获取content形式的文件路径的真实路径
            String path = getFilePath();
            if (!TextUtils.isEmpty(path) && new File(path).exists()) {
                mInputUri = SdkUtils.isQ() ? mInputUri : Uri.fromFile(new File(path));
            } else {
                try {
                    copyFile(mInputUri, mOutputUri);
                } catch (NullPointerException | IOException e) {
                    Log.e(TAG, "Copying failed", e);
                    throw e;
                }
            }
        } else if (!"file".equals(inputUriScheme)) {
            Log.e(TAG, "Invalid Uri scheme " + inputUriScheme);
            throw new IllegalArgumentException("Invalid Uri scheme" + inputUriScheme);
        }
    }

    /**
     * 获取文件真实路径
     *
     * @return
     */
    private String getFilePath() {
        if (ContextCompat.checkSelfPermission(getContext(), permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return FileUtils.getPath(getContext(), mInputUri);
        } else {
            return null;
        }
    }

    /**
     * 拷贝文件
     *
     * @param inputUri  原始图片地址
     * @param outputUri 目的图片地址
     */
    private void copyFile(@NonNull Uri inputUri, @Nullable Uri outputUri) throws NullPointerException, IOException {
        Log.d(TAG, "copyFile");

        if (outputUri == null) {
            throw new NullPointerException("Output Uri is null - cannot copy image");
        }

        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = getContext().getContentResolver().openInputStream(inputUri);
            outputStream = new FileOutputStream(new File(outputUri.getPath()));
            if (inputStream == null) {
                throw new NullPointerException("InputStream for given input Uri is null");
            }

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        } finally {
            BitmapLoadUtils.close(outputStream);
            BitmapLoadUtils.close(inputStream);

            // swap uris, because input image was copied to the output destination
            // (cropped image will override it later)
            mInputUri = mOutputUri;
        }
    }

    /**
     * 下载网络图片
     */
    private void downloadFile(@NonNull Uri inputUri, @Nullable Uri outputUri) throws NullPointerException, IOException {
        Log.d(TAG, "downloadFile");
        if (outputUri == null) {
            throw new NullPointerException("Output Uri is null - cannot download image");
        }
        OutputStream outputStream = null;
        BufferedInputStream bin = null;
        BufferedOutputStream bout = null;
        try {
            URL u = new URL(inputUri.toString());
            byte[] buffer = new byte[1024];
            int read;
            bin = new BufferedInputStream(u.openStream());
            outputStream = getContext().getContentResolver().openOutputStream(outputUri);
            if (outputStream != null) {
                bout = new BufferedOutputStream(outputStream);
                while ((read = bin.read(buffer)) > -1) {
                    bout.write(buffer, 0, read);
                }
                bout.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // swap uris, because input image was downloaded to the output destination
            // (cropped image will override it later)

            //网络图片已经下载到本地,所以mInputUri直接使用下载好的图片地址
            mInputUri = mOutputUri;

            BitmapLoadUtils.close(bout);
            BitmapLoadUtils.close(bin);
            BitmapLoadUtils.close(outputStream);
        }
    }

    @Override
    protected void onPostExecute(@NonNull BitmapWorkerResult result) {
        if (result.mBitmapWorkerException == null) {
            String inputUriString = mInputUri.toString();
            mBitmapLoadCallback.onBitmapLoaded(result.mBitmapResult, result.mExifInfo,
                    MimeType.isContent(inputUriString) ? inputUriString : mInputUri.getPath(),
                    (mOutputUri == null) ? null : mOutputUri.getPath());
        } else {
            mBitmapLoadCallback.onFailure(result.mBitmapWorkerException);
        }
    }

}
