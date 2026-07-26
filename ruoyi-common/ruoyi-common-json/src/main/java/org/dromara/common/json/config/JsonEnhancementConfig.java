package org.dromara.common.json.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.json.enhance.JsonFieldProcessor;
import org.dromara.common.json.enhance.JsonValueEnhancer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * JSON 响应增强链配置。
 */
@AutoConfiguration
public class JsonEnhancementConfig {

    @Bean
    public JsonValueEnhancer jsonValueEnhancer(ObjectMapper objectMapper,
                                               List<JsonFieldProcessor> processors) {
        return new JsonValueEnhancer(objectMapper, processors);
    }
}
