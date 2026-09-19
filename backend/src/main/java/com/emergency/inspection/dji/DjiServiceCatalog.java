package com.emergency.inspection.dji;

import com.emergency.inspection.entity.Device;

import java.util.List;

/**
 * 可用服务(指令)目录。
 *
 * 注意上云 API 的架构:机场是网关设备,无人机是挂在机场下的子设备。
 * 因此**绝大多数指令都下发到机场的 services 主题**(包括控制飞行器的 drone_open / return_home),
 * 平台侧不直接向无人机下发。无人机行记录只保留少量与其自身相关的指令。
 */
public final class DjiServiceCatalog {

    private DjiServiceCatalog() {
    }

    /**
     * @param method  上云 API 的方法名
     * @param label   界面展示名
     * @param group   分组
     * @param danger  是否为有副作用的操作(界面需二次确认)
     */
    public record ServiceDef(String method, String label, String group, boolean danger) {
    }

    /** 机场指令 */
    private static final List<ServiceDef> DOCK_SERVICES = List.of(
            new ServiceDef("cover_open", "打开舱盖", "舱体控制", false),
            new ServiceDef("cover_close", "关闭舱盖", "舱体控制", false),
            new ServiceDef("cover_force_close", "强制关舱盖", "舱体控制", true),
            new ServiceDef("putter_open", "推杆展开", "舱体控制", false),
            new ServiceDef("putter_close", "推杆闭合", "舱体控制", false),

            new ServiceDef("drone_open", "飞行器开机", "飞行器", false),
            new ServiceDef("drone_close", "飞行器关机", "飞行器", false),
            new ServiceDef("drone_self_check", "一键自检", "飞行器", false),
            new ServiceDef("return_home", "一键返航", "飞行器", true),
            new ServiceDef("return_home_cancel", "取消返航", "飞行器", false),
            new ServiceDef("drone_format", "飞行器数据格式化", "飞行器", true),

            new ServiceDef("charge_open", "开启充电", "充电", false),
            new ServiceDef("charge_close", "关闭充电", "充电", false),

            new ServiceDef("supplement_light_open", "开启补光灯", "辅助", false),
            new ServiceDef("supplement_light_close", "关闭补光灯", "辅助", false),
            new ServiceDef("debug_mode_open", "开启调试模式", "辅助", false),
            new ServiceDef("debug_mode_close", "关闭调试模式", "辅助", false),
            new ServiceDef("rtk_calibration", "一键标定", "辅助", true),

            new ServiceDef("device_reboot", "机场重启", "系统", true),
            new ServiceDef("device_format", "机场数据格式化", "系统", true));

    /** 飞行器侧指令(仍经所属机场下发) */
    private static final List<ServiceDef> DRONE_SERVICES = List.of(
            new ServiceDef("drone_open", "飞行器开机", "飞行器", false),
            new ServiceDef("drone_close", "飞行器关机", "飞行器", false),
            new ServiceDef("return_home", "一键返航", "飞行器", true),
            new ServiceDef("return_home_cancel", "取消返航", "飞行器", false));

    public static List<ServiceDef> of(Device.DeviceType type) {
        return type == Device.DeviceType.DOCK ? DOCK_SERVICES : DRONE_SERVICES;
    }

    /** 校验指令是否属于该设备类型的合法指令,避免任意 method 被透传到设备 */
    public static boolean isAllowed(Device.DeviceType type, String method) {
        return of(type).stream().anyMatch(s -> s.method().equals(method));
    }
}
