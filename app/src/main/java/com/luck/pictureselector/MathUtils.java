/*
 * Copyright 2016 GcsSloop
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Last modified 2016-12-03 22:55:55
 *
 */

package com.luck.pictureselector;

import android.graphics.Point;
import android.graphics.PointF;

public class MathUtils {

    public MathUtils() {
    }

    /**
     * Get the distance between two points.
     * 获取两点之间的距离
     *
     * @param A Point A
     * @param B Point B
     * @return the distance between point A and point B.
     */
    public static int getDistance(PointF A, PointF B) {
        return (int) Math.sqrt(Math.pow(A.x - B.x, 2) + Math.pow(A.y - B.y, 2));
    }

    /**
     * Get the distance between two points.
     * 获取两点之间的距离
     */
    public static int getDistance(float x1, float y1, float x2, float y2) {
        return (int) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    /**
     * Get the coordinates of a point on the line by cut length.
     *
     * @param A         Point A
     * @param B         Point B
     * @param cutLength cut length
     * @return the point.
     */
    public static Point getPointByCutLength(Point A, Point B, int cutLength) {
        float radian = getRadian(A, B);
        return new Point(A.x + (int) (cutLength * Math.cos(radian)), A.y + (int) (cutLength * Math.sin(radian)));
    }

    /**
     * Get the radian between current line(determined by point A and B) and horizontal line.
     * 获取两点之间的弧度
     *
     * @param A point A
     * @param B point B
     * @return the radian
     */
    public static float getRadian(Point A, Point B) {
        float lenA = B.x - A.x;
        float lenB = B.y - A.y;
        float lenC = (float) Math.sqrt(lenA * lenA + lenB * lenB);
        float radian = (float) Math.acos(lenA / lenC);
        radian = radian * (B.y < A.y ? -1 : 1);
        return radian;
    }

    /**
     * 计算两个点与圆点构成的直线之间的夹角,利用余弦公式计算两条线之间的夹角
     */
    public static float calculateAngleBetweenLinesByCos(Point A, Point B) {
        return calculateAngleBetweenLinesByCos(A.x, A.y, B.x, B.y);
    }

    /**
     * 计算两个点与圆点构成的直线之间的夹角,利用余弦公式计算两条线之间的夹角
     */
    public static float calculateAngleBetweenLinesByCos(float fx2, float fy2, float sx2, float sy2) {
        return calculateAngleBetweenLinesByCos(0, 0, fx2, fy2, 0, 0, sx2, sy2);
    }

    /**
     * 利用余弦公式计算两条线之间的夹角
     */
    public static float calculateAngleBetweenLinesByCos(float fx1, float fy1, float fx2, float fy2,
                                                        float sx1, float sy1, float sx2, float sy2) {

        //余弦公式:向量1点乘向量2/(向量1模长*向量2模长)
        //(fx2-fx1,fy2-fy1)*(sx2-sx1,sy2-sy1)/(向量1模长*向量2模长)
        float v1DotMulV2 = (fx2 - fx1) * (sx2 - sx1) + (fy2 - fy1) * (sy2 - sy1);
        double v1Norm = Math.sqrt(Math.pow((fx2 - fx1), 2) + Math.pow((fy2 - fy1), 2));
        double v2Norm = Math.sqrt(Math.pow((sx2 - sx1), 2) + Math.pow((sy2 - sy1), 2));
        double cosAngle = v1DotMulV2 / (v1Norm * v2Norm);
        double angle = Math.toDegrees(Math.acos(cosAngle));

        return (float) angle;
    }

    /**
     * 计算两条线之间的夹角
     */
    public static float calculateAngleBetweenLines(float fx1, float fy1, float fx2, float fy2,
                                                   float sx1, float sy1, float sx2, float sy2) {
        return calculateAngleDelta(
                (float) Math.toDegrees((float) Math.atan2((fy1 - fy2), (fx1 - fx2))),
                (float) Math.toDegrees((float) Math.atan2((sy1 - sy2), (sx1 - sx2))));
    }

    public static float calculateAngleDelta(float angleFrom, float angleTo) {
        float angle = angleTo % 360.0f - angleFrom % 360.0f;

        if (angle < -180.0f) {
            angle += 360.0f;
        } else if (angle > 180.0f) {
            angle -= 360.0f;
        }

        return angle;
    }

    /**
     * angle to radian
     * 角度转弧度
     *
     * @param angle angle
     * @return radian
     */
    public static double angle2Radian(double angle) {
        return angle / 180 * Math.PI;
    }

    /**
     * radian to angle
     * 弧度转角度
     *
     * @param radian radian
     * @return angle
     */
    public static double radian2Angle(double radian) {
        return radian / Math.PI * 180;
    }
}
