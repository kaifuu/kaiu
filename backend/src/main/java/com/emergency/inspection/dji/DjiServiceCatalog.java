package com.emergency.inspection.dji;

import com.emergency.inspection.entity.Device;

import java.util.List;

/**
 * 可用服务(指令)目录。
 *
 * 注意上云 API 的架构:机场是网关设备,无人机是挂在机场下的子设备。
 * 因此**绝大多数指令都下发到机场的 services 主题**(包括控制飞行器的 drone_open / return_home),
 * 平台侧不直接向无人机下发。无人机行记录只保留少量与其自身相关的指令。
 *
 * console 标记:是否在机场控制页「指令单元」操作台展示。
 * 航线任务 / OTA / 远程日志 / 一键起飞等带参数或由专门页面编排的指令不进操作台,
 * 它们集中在「远程调试」试验台与各自的业务 TAB。
 */
public final class DjiServiceCatalog {

    private DjiServiceCatalog() {
    }

    /**
     * 指令参数定义:远程调试 TAB 据此动态生成参数表单。
     */
    public record ParamDef(String key, String label, String type, boolean required,
                           String defaultValue, String placeholder) {
    }

    /**
     * @param method  上云 API 的方法名
     * @param label   界面展示名
     * @param group   分组
     * @param danger  是否为有副作用的操作(界面需二次确认)
     * @param params  参数定义(无参数指令为 null)
     * @param console 是否进入指令单元操作台
     */
    public record ServiceDef(String method, String label, String group, boolean danger,
                             List<ParamDef> params, boolean console) {

        /** 常规无参指令:进入操作台 */
        static ServiceDef cmd(String method, String label, String group, boolean danger) {
            return new ServiceDef(method, label, group, danger, null, true);
        }

        /** 专项页面编排的指令:不进操作台,仅出现在指令目录与远程调试试验台 */
        static ServiceDef hidden(String method, String label, String group, boolean danger) {
            return new ServiceDef(method, label, group, danger, null, false);
        }

        /** 带参数指令:不进操作台,参数表单由远程调试 TAB 动态渲染 */
        static ServiceDef param(String method, String label, String group, boolean danger,
                                List<ParamDef> params) {
            return new ServiceDef(method, label, group, danger, params, false);
        }
    }

    /** 机场指令 */
    private static final List<ServiceDef> DOCK_SERVICES = List.of(
            ServiceDef.cmd("cover_open", "打开舱盖", "舱体控制", false),
            ServiceDef.cmd("cover_close", "关闭舱盖", "舱体控制", false),
            ServiceDef.cmd("cover_force_close", "强制关舱盖", "舱体控制", true),
            ServiceDef.cmd("putter_open", "推杆展开", "舱体控制", false),
            ServiceDef.cmd("putter_close", "推杆闭合", "舱体控制", false),

            ServiceDef.cmd("drone_open", "飞行器开机", "飞行器", false),
            ServiceDef.cmd("drone_close", "飞行器关机", "飞行器", false),
            ServiceDef.cmd("drone_self_check", "一键自检", "飞行器", false),
            ServiceDef.cmd("return_home", "一键返航", "飞行器", true),
            ServiceDef.cmd("return_home_cancel", "取消返航", "飞行器", false),
            ServiceDef.cmd("drone_format", "飞行器数据格式化", "飞行器", true),

            ServiceDef.cmd("charge_open", "开启充电", "充电", false),
            ServiceDef.cmd("charge_close", "关闭充电", "充电", false),

            ServiceDef.cmd("supplement_light_open", "开启补光灯", "辅助", false),
            ServiceDef.cmd("supplement_light_close", "关闭补光灯", "辅助", false),
            ServiceDef.cmd("debug_mode_open", "开启调试模式", "辅助", false),
            ServiceDef.cmd("debug_mode_close", "关闭调试模式", "辅助", false),
            ServiceDef.cmd("rtk_calibration", "一键标定", "辅助", true),

            ServiceDef.cmd("device_reboot", "机场重启", "系统", true),
            ServiceDef.cmd("device_format", "机场数据格式化", "系统", true),

            // ---- 航线任务:由航线任务 TAB 编排,prepare 成功后自动 execute ----
            ServiceDef.hidden("flighttask_prepare", "下发航线任务", "航线任务", false),
            ServiceDef.hidden("flighttask_execute", "执行航线任务", "航线任务", false),
            ServiceDef.hidden("flighttask_undo", "取消航线任务", "航线任务", false),

            // ---- 远程调试:一键起飞需指定目标点,经调试试验台带参下发 ----
            ServiceDef.param("takeoff_to_point", "一键起飞(指点)", "远程调试", true, List.of(
                    new ParamDef("longitude", "目标经度", "number", true, null, "如 116.397428"),
                    new ParamDef("latitude", "目标纬度", "number", true, null, "如 39.909230"),
                    new ParamDef("height", "起飞高度(m)", "number", true, "60", "相对机场高度"))),

            // ---- 固件升级:固件 TAB 组装 ota_create 载荷 ----
            ServiceDef.hidden("ota_create", "固件升级下发", "固件升级", true),

            // ---- 远程日志:日志 TAB 编排列表拉取与上传 ----
            ServiceDef.hidden("logs_file_list", "拉取日志列表", "远程日志", false),
            ServiceDef.hidden("logs_file_upload", "上传日志文件", "远程日志", false));

    /** 飞行器侧指令(仍经所属机场下发) */
    private static final List<ServiceDef> DRONE_SERVICES = List.of(
            ServiceDef.cmd("drone_open", "飞行器开机", "飞行器", false),
            ServiceDef.cmd("drone_close", "飞行器关机", "飞行器", false),
            ServiceDef.cmd("return_home", "一键返航", "飞行器", true),
            ServiceDef.cmd("return_home_cancel", "取消返航", "飞行器", false));

    public static List<ServiceDef> of(Device.DeviceType type) {
        return type == Device.DeviceType.DOCK ? DOCK_SERVICES : DRONE_SERVICES;
    }

    /** 校验指令是否属于该设备类型的合法指令,避免任意 method 被透传到设备 */
    public static boolean isAllowed(Device.DeviceType type, String method) {
        return of(type).stream().anyMatch(s -> s.method().equals(method));
    }
}
