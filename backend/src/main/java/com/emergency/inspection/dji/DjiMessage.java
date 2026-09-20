package com.emergency.inspection.dji;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * 上云 API 的统一报文信封:{ tid, bid, timestamp, method, data }。
 * - tid 事务 id:一次请求/回复配对,云端下发指令时生成,设备回复时原样带回
 * - bid 业务 id:设备主动上报时生成
 */
public record DjiMessage(String tid, String bid, Long timestamp, String method, JsonNode data) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static DjiMessage parse(String payload) {
        try {
            JsonNode root = MAPPER.readTree(payload);
            return new DjiMessage(
                    text(root, "tid"),
                    text(root, "bid"),
                    root.hasNonNull("timestamp") ? root.get("timestamp").asLong() : null,
                    text(root, "method"),
                    root.has("data") ? root.get("data") : MAPPER.createObjectNode());
        } catch (Exception e) {
            throw new IllegalArgumentException("非法的大疆报文: " + e.getMessage(), e);
        }
    }

    /** 构造云端下行报文(指令下发),tid 由调用方传入以便与回复配对 */
    public static String build(String tid, String method, Object data) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("tid", tid);
        root.put("bid", UUID.randomUUID().toString());
        root.put("timestamp", System.currentTimeMillis());
        root.put("method", method);
        root.set("data", MAPPER.valueToTree(data == null ? MAPPER.createObjectNode() : data));
        return root.toString();
    }

    /** 构造云端对设备请求/事件的回复报文(带回设备发来的 tid/bid) */
    public static String buildReply(String tid, String bid, String method, Object data) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("tid", tid);
        root.put("bid", bid);
        root.put("timestamp", System.currentTimeMillis());
        root.put("method", method);
        root.set("data", MAPPER.valueToTree(data == null ? MAPPER.createObjectNode() : data));
        return root.toString();
    }

    /**
     * 构造属性设置报文(property/set)。
     * 与服务调用不同,属性设置没有 method 字段,data 即为要写入的属性集合
     * (如 AI 识别配置:{ai_switch, ai_follow_switch, ...})。
     */
    public static String buildProperty(String tid, Object data) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("tid", tid);
        root.put("bid", UUID.randomUUID().toString());
        root.put("timestamp", System.currentTimeMillis());
        root.set("data", MAPPER.valueToTree(data == null ? MAPPER.createObjectNode() : data));
        return root.toString();
    }

    /** 设备回复里的业务返回码:0 表示成功 */
    public Integer resultCode() {
        if (data == null || !data.hasNonNull("result")) {
            return null;
        }
        return data.get("result").asInt();
    }

    public boolean success() {
        Integer code = resultCode();
        return code != null && code == 0;
    }

    public String dataText(String field) {
        return data != null && data.hasNonNull(field) ? data.get(field).asText() : null;
    }

    public Integer dataInt(String field) {
        return data != null && data.hasNonNull(field) ? data.get(field).asInt() : null;
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
