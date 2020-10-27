package com.yalantis.ucrop.callback;

/**
 * Interface for crop bound change notifying.
 * 图片裁剪边界变化事件监听器
 */
public interface CropBoundsChangeListener {

    void onCropAspectRatioChanged(float cropRatio);

}