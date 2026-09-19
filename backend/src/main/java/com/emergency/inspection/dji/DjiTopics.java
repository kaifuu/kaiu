package com.emergency.inspection.dji;

/**
 * 大疆上云 API 主题定义。
 *
 * 约定:{gateway_sn} 为网关设备(机场)序列号,{device_sn} 为物模型所属设备序列号。
 * 机场是网关,无人机是挂在机场下的子设备 —— 因此「指令下发」等下行主题用的是网关 SN,
 * 而「属性上报」用的是设备自身 SN。
 */
public final class DjiTopics {

    private DjiTopics() {
    }

    private static final String THING = "thing/product/";
    private static final String SYS = "sys/product/";

    /* ---------------- 上行:设备 → 云端 ---------------- */

    /** 设备属性定频推送(0.5Hz) */
    public static final String OSD = THING + "%s/osd";

    /** 设备状态变化推送 */
    public static final String STATE = THING + "%s/state";

    /** 服务调用回复 */
    public static final String SERVICES_REPLY = THING + "%s/services_reply";

    /** 设备事件上报(任务进度 / HMS 等) */
    public static final String EVENTS = THING + "%s/events";

    /** 设备向云端请求 */
    public static final String REQUESTS = THING + "%s/requests";

    /** 属性设置回复 */
    public static final String PROPERTY_SET_REPLY = THING + "%s/property/set_reply";

    /** 设备上下线与拓扑更新(网关 SN) */
    public static final String STATUS = SYS + "%s/status";

    /* ---------------- 下行:云端 → 设备 ---------------- */

    /** 服务调用(指令下发) */
    public static final String SERVICES = THING + "%s/services";

    /** 事件回复 */
    public static final String EVENTS_REPLY = THING + "%s/events_reply";

    /** 请求回复 */
    public static final String REQUESTS_REPLY = THING + "%s/requests_reply";

    /** 属性设置 */
    public static final String PROPERTY_SET = THING + "%s/property/set";

    /** 拓扑更新回复 */
    public static final String STATUS_REPLY = SYS + "%s/status_reply";

    /* ---------------- 工具 ---------------- */

    public static String services(String gatewaySn) {
        return String.format(SERVICES, gatewaySn);
    }

    public static String eventsReply(String gatewaySn) {
        return String.format(EVENTS_REPLY, gatewaySn);
    }

    public static String requestsReply(String gatewaySn) {
        return String.format(REQUESTS_REPLY, gatewaySn);
    }

    public static String propertySet(String gatewaySn) {
        return String.format(PROPERTY_SET, gatewaySn);
    }

    public static String statusReply(String gatewaySn) {
        return String.format(STATUS_REPLY, gatewaySn);
    }

    /**
     * 从主题中取出设备 SN,并判断属于哪类上行主题。
     * 例:thing/product/SN123/osd → (SN123, "osd")
     * 不属于上云 API 主题时返回 null。
     */
    public static TopicParts parse(String topic) {
        if (topic == null) {
            return null;
        }
        if (topic.startsWith(THING)) {
            String rest = topic.substring(THING.length());
            int slash = rest.indexOf('/');
            if (slash <= 0) {
                return null;
            }
            return new TopicParts(rest.substring(0, slash), rest.substring(slash + 1), false);
        }
        if (topic.startsWith(SYS)) {
            String rest = topic.substring(SYS.length());
            int slash = rest.indexOf('/');
            if (slash <= 0) {
                return null;
            }
            return new TopicParts(rest.substring(0, slash), rest.substring(slash + 1), true);
        }
        return null;
    }

    /** 解析结果:suffix 为 SN 之后的主题后缀,如 osd / services_reply */
    public record TopicParts(String sn, String suffix, boolean system) {
    }
}
