package com.emergency.inspection.dji;

/**
 * 上云 API 的 type / sub_type → 机型名。
 *
 * 大疆在 update_topo 里用数字编码机型,官方文档只给出设备模型键(如 0-67-0),
 * 这里的映射覆盖常见机型,未收录的一律回落成「类型 x-y」原样展示 ——
 * 机型名在设备台账里可手工修改,不依赖本表准确。
 */
public final class DjiModel {

    private DjiModel() {
    }

    public static String name(Integer type, Integer subType) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case 60 -> "M300 RTK";
            case 67 -> "M30 系列";
            case 77 -> "M30T";
            case 89 -> "Mavic 3E/3T";
            case 91 -> "M3E 系列";
            case 98 -> "DJI Dock";
            case 99 -> "DJI Dock(旧固件)";
            case 116 -> "M3D/M3TD";
            case 119 -> "DJI Dock 2";
            default -> "类型 " + type + "-" + (subType == null ? 0 : subType);
        };
    }

    /** 设备类型枚举名,仅用于日志 */
    public static String typeName(Integer type) {
        if (type == null) {
            return "unknown";
        }
        return switch (type) {
            case 98, 99, 119 -> "DOCK";
            default -> "DRONE";
        };
    }
}
