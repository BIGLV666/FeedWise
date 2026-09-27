package com.feedwise.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON Schema 校验器（覆盖本项目工具 schema 用到的关键字：
 * type / required / properties / minLength / maxLength / enum / minItems / maxItems）。
 *
 * <p>作用：模型给出的参数在进入工具实现前先过一道结构校验，
 * 结构不合格直接拒绝，不给业务代码喂脏数据。</p>
 */
@Component
public class AiSchemaValidator {

    /**
     * 校验参数是否符合 schema。
     *
     * @param args  模型入参
     * @param schema 工具声明的 JSON Schema
     * @return 校验错误列表；空列表表示通过
     */
    public List<String> validate(JsonNode args, Map<String, Object> schema) {
        List<String> errors = new ArrayList<>();
        if (args == null || !args.isObject()) {
            errors.add("参数必须是 JSON 对象");
            return errors;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        Object requiredObj = schema.getOrDefault("required", List.of());
        for (Object req : (Iterable<Object>) requiredObj) {
            if (!args.has(req.toString()) || args.path(req.toString()).isNull()) {
                errors.add("缺少必填参数: " + req);
            }
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String field = entry.getKey();
            if (!args.has(field) || args.path(field).isNull()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rule = (Map<String, Object>) entry.getValue();
            JsonNode value = args.path(field);
            String type = String.valueOf(rule.getOrDefault("type", "string"));
            switch (type) {
                case "string" -> {
                    if (!value.isTextual()) {
                        errors.add(field + " 必须是字符串");
                        continue;
                    }
                    int len = value.asText().length();
                    if (rule.containsKey("minLength") && len < (int) rule.get("minLength")) {
                        errors.add(field + " 长度不足（最少 " + rule.get("minLength") + "）");
                    }
                    if (rule.containsKey("maxLength") && len > (int) rule.get("maxLength")) {
                        errors.add(field + " 超长（最多 " + rule.get("maxLength") + "）");
                    }
                    if (rule.containsKey("enum")) {
                        @SuppressWarnings("unchecked")
                        List<Object> allowed = (List<Object>) rule.get("enum");
                        if (allowed.stream().noneMatch(a -> a.toString().equals(value.asText()))) {
                            errors.add(field + " 取值不在枚举范围内: " + value.asText());
                        }
                    }
                }
                case "integer" -> {
                    if (!value.canConvertToLong()) {
                        errors.add(field + " 必须是整数");
                    }
                }
                case "array" -> {
                    if (!value.isArray()) {
                        errors.add(field + " 必须是数组");
                        continue;
                    }
                    int size = value.size();
                    if (rule.containsKey("minItems") && size < (int) rule.get("minItems")) {
                        errors.add(field + " 元素过少（最少 " + rule.get("minItems") + "）");
                    }
                    if (rule.containsKey("maxItems") && size > (int) rule.get("maxItems")) {
                        errors.add(field + " 元素过多（最多 " + rule.get("maxItems") + "）");
                    }
                }
                default -> {
                    // 未识别的类型放行，由工具实现内的业务校验兜底
                }
            }
        }
        return errors;
    }
}
