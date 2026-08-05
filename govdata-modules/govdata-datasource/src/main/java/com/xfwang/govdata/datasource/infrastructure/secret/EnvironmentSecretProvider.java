package com.xfwang.govdata.datasource.infrastructure.secret;

import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretValue;
import com.xfwang.platform.api.secret.SecretResolveRequest;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 第一阶段环境变量 SecretProvider。
 *
 * <p>引用格式为 {@code env:USERNAME_VARIABLE,PASSWORD_VARIABLE}，仓库和数据库只保存变量名。</p>
 */
@Component
public class EnvironmentSecretProvider implements SecretProvider {

    @Override
    public Optional<SecretValue> resolve(SecretResolveRequest request) {
        String secretRef = request == null ? null : request.getSecretRef();
        if (StringUtils.isBlank(secretRef) || !secretRef.startsWith("env:")) {
            return Optional.empty();
        }
        String[] variables = secretRef.substring(4).split(",", -1);
        if (variables.length != 2 || StringUtils.isBlank(variables[0]) || StringUtils.isBlank(variables[1])) {
            return Optional.empty();
        }
        String username = System.getenv(variables[0].trim());
        String password = System.getenv(variables[1].trim());
        if (username == null || password == null) {
            return Optional.empty();
        }
        return Optional.of(new SecretValue(Map.of(
            "username", username.toCharArray(),
            "password", password.toCharArray())));
    }
}
