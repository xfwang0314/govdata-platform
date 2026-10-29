package org.dromara.common.web.interceptor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlusWebInvokeTimeInterceptorTest {

    @Test
    void requestParametersRemoveCredentialsCaseInsensitively() {
        Map<String, String[]> parameters = new LinkedHashMap<>();
        parameters.put("datasourceId", new String[]{"1"});
        parameters.put("Authorization", new String[]{"Bearer sensitive-token"});
        parameters.put("accessToken", new String[]{"sensitive-access-token"});

        Map<String, String[]> sanitized = PlusWebInvokeTimeInterceptor.sanitizeParameters(parameters);

        assertThat(sanitized).containsOnlyKeys("datasourceId");
        assertThat(sanitized.toString()).doesNotContain("sensitive-token", "sensitive-access-token");
    }
}
