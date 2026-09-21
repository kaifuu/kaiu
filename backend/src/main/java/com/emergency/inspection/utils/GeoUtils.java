package com.emergency.inspection.utils;

import com.emergency.inspection.entity.GeoFence;

import java.util.List;
import java.util.Map;

/**
 * 围栏空间判定(纯 Java,不依赖 PostGIS):
 * 多边形用射线法,圆用 Haversine 大圆距离。坐标为裸经纬度。
 */
public final class GeoUtils {

    private static final double EARTH_R = 6371008.8; // 平均地球半径(米)

    private GeoUtils() {
    }

    /** Haversine 大圆距离(米) */
    public static double haversineMeters(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_R * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** 射线法:点是否在多边形内(边界按穿越计数奇偶判定) */
    public static boolean pointInPolygon(double lng, double lat, List<Map<String, Object>> pts) {
        if (pts == null || pts.size() < 3) {
            return false;
        }
        boolean inside = false;
        int n = pts.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = toD(pts.get(i).get("lng")), yi = toD(pts.get(i).get("lat"));
            double xj = toD(pts.get(j).get("lng")), yj = toD(pts.get(j).get("lat"));
            boolean cross = (yi > lat) != (yj > lat)
                    && lng < (xj - xi) * (lat - yi) / (yj - yi + 1e-12) + xi;
            if (cross) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** 点是否落在围栏内(按围栏形状分派) */
    public static boolean contains(GeoFence fence, double lng, double lat, List<Map<String, Object>> pts) {
        if (fence.getShape() == GeoFence.Shape.CIRCLE) {
            double r = fence.getRadius() == null ? 0 : fence.getRadius().doubleValue();
            if (pts.isEmpty()) {
                return false;
            }
            double cx = toD(pts.get(0).get("lng")), cy = toD(pts.get(0).get("lat"));
            return haversineMeters(lng, lat, cx, cy) <= r;
        }
        return pointInPolygon(lng, lat, pts);
    }

    /** 沿当前速度做线性外推:返回 horizon 秒后的位置(度/秒速度) */
    public static double[] extrapolate(double lng, double lat, double vLng, double vLat, double horizonSeconds) {
        double mPerDegLat = 111320.0;
        double mPerDegLng = 111320.0 * Math.cos(Math.toRadians(lat));
        return new double[]{
                lng + (vLng * horizonSeconds) / mPerDegLng,
                lat + (vLat * horizonSeconds) / mPerDegLat
        };
    }

    private static double toD(Object v) {
        return v == null ? 0 : Double.parseDouble(String.valueOf(v));
    }
}
