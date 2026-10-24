package com.example.campushelp.ai;

import com.example.campushelp.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;

/** Narrow, explainable egress checks, not a complete DLP system or an identity detector. */
@Component @RequiredArgsConstructor
public class PromptContext {
    public static final String VERSION="selected-category-last20-bounded-v1";
    private static final Pattern SENSITIVE=Pattern.compile("(?i)sk-[a-z0-9_-]{16,}|Bearer\\s+[a-z0-9._-]{16,}|(?<![0-9])1[3-9][0-9]{9}(?![0-9])|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}|(?:取件码|验证码|密码|口令|提取码|api[ _-]?key)\\s*[:：=是为]?\\s*[a-z0-9_-]{3,}");
    private final ObjectMapper json;
    public List<Map<String,Object>> prepare(List<Map<String,Object>> original,Map<String,Object> tool) throws Exception {
        var result=new ArrayList<>(original);
        for(var message:result) {
            if(!(message.get("content") instanceof String text)) throw new BusinessException(422,"模型上下文格式不支持，请使用完整表单");
            if(SENSITIVE.matcher(text).find()) throw new BusinessException(422,"当前上下文含疑似取件码、密码、密钥或联系方式，未发送给模型；请移除敏感内容或使用完整表单");
        }
        while(result.size()>2 && bytes(result,tool)>ModelControls.MAX_PAYLOAD_BYTES-4096) result.remove(1);
        if(bytes(result,tool)>ModelControls.MAX_PAYLOAD_BYTES-4096) throw new BusinessException(422,"当前需求内容过长，未发送给模型；请缩小问题范围或使用完整表单");
        return List.copyOf(result);
    }
    private int bytes(List<Map<String,Object>> messages,Map<String,Object> tool) throws Exception {return json.writeValueAsBytes(Map.of("messages",messages,"tool",tool)).length;}
}
