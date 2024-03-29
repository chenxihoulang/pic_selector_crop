package com.yalantis.ucrop.util;

import android.graphics.RectF;

public class RectUtils {

    /**
     * Gets a float array of the 2D coordinates representing a rectangles
     * corners.
     * The order of the corners in the float array is:
     * 0------->1
     * ^        |
     * |        |
     * |        v
     * 3<-------2
     * <p>
     * 获取矩形的4个顶点的位置
     *
     * @param r the rectangle to get the corners of
     * @return the float array of corners (8 floats)
     */
    public static float[] getCornersFromRect(RectF r) {
        return new float[]{
                r.left, r.top,
                r.right, r.top,
                r.right, r.bottom,
                r.left, r.bottom
        };
    }

    /**
     * Gets a float array of two lengths representing a rectangles width and height
     * The order of the corners in the input float array is:
     * 0------->1
     * ^        |
     * |        |
     * |        v
     * 3<-------2
     * 根据4个顶点的坐标位置,获取矩形的边长,想象矩形有可能已经旋转过某个角度,这个时候边长其实是三角形的斜边长
     *
     * @param corners the float array of corners (8 floats)
     * @return the float array of width and height (2 floats)
     */
    public static float[] getRectSidesFromCorners(float[] corners) {
        return new float[]{(float) Math.sqrt(Math.pow(corners[0] - corners[2], 2) + Math.pow(corners[1] - corners[3], 2)),
                (float) Math.sqrt(Math.pow(corners[2] - corners[4], 2) + Math.pow(corners[3] - corners[5], 2))};
    }

    /**
     * 获取矩形的中心点位置
     *
     * @param r
     * @return
     */
    public static float[] getCenterFromRect(RectF r) {
        return new float[]{r.centerX(), r.centerY()};
    }

    /**
     * Takes an array of 2D coordinates representing corners and returns the
     * smallest rectangle containing those coordinates.
     * <p>
     * 根据4个顶点的坐标,返回包含这4个顶点的最小矩形
     *
     * @param array array of 2D coordinates
     * @return smallest rectangle containing coordinates
     */
    public static RectF trapToRect(float[] array) {
        RectF r = new RectF(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

        for (int i = 1; i < array.length; i += 2) {
            float x = Math.round(array[i - 1] * 10) / 10.f;
            float y = Math.round(array[i] * 10) / 10.f;

            //左边和上边取小值
            r.left = (x < r.left) ? x : r.left;
            r.top = (y < r.top) ? y : r.top;

            //右边和下边取大值
            r.right = (x > r.right) ? x : r.right;
            r.bottom = (y > r.bottom) ? y : r.bottom;
        }

        //如果矩形翻转了,则交换左右或上下的值
        r.sort();

        return r;
    }

}