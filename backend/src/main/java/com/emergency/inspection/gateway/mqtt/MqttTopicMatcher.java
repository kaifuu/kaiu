package com.emergency.inspection.gateway.mqtt;

/**
 * MQTT 主题过滤器匹配(MQTT 3.1.1 第 4.7 节):
 * - `+` 匹配单层,如 thing/product/+/osd 匹配 thing/product/SN1/osd
 * - `#` 匹配剩余多层(只能出现在末尾),如 thing/# 匹配 thing/a/b
 * - 以 $ 开头的主题不参与通配匹配
 */
public final class MqttTopicMatcher {

    private MqttTopicMatcher() {
    }

    public static boolean matches(String filter, String topic) {
        if (filter == null || topic == null) {
            return false;
        }
        if (filter.equals(topic)) {
            return true;
        }
        String[] f = filter.split("/", -1);
        String[] t = topic.split("/", -1);

        for (int i = 0; i < f.length; i++) {
            String seg = f[i];
            if ("#".equals(seg)) {
                // '#' 必须独占末层;出现即匹配剩余全部(含零层)
                return i == f.length - 1;
            }
            if (i >= t.length) {
                return false;
            }
            if (!"+".equals(seg) && !seg.equals(t[i])) {
                return false;
            }
        }
        return f.length == t.length;
    }
}
