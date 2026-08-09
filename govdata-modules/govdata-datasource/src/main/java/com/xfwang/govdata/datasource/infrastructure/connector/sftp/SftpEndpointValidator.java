package com.xfwang.govdata.datasource.infrastructure.connector.sftp;

import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectorEndpointValidationContext;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectorEndpointValidator;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** SFTP 路径和主机指纹配置校验。 */
@Component
public class SftpEndpointValidator implements ConnectorEndpointValidator {

    private static final Pattern FINGERPRINT = Pattern.compile("^(SHA256:[A-Za-z0-9+/]{20,100}={0,2}|[0-9a-fA-F:]{47,95})$");

    @Override
    public boolean supports(String connectorCode, String connectorVersion) {
        return "sftp".equals(connectorCode) && "1.0".equals(connectorVersion);
    }

    @Override
    public void validate(ConnectorEndpointValidationContext context) {
        String rootPath = text(context, "rootPath");
        if (!rootPath.startsWith("/") || rootPath.contains("..") || rootPath.contains("\\")
            || rootPath.chars().anyMatch(Character::isISOControl) || rootPath.length() > 500) {
            throw invalid("SFTP 根目录必须是安全的绝对路径");
        }
        if (!FINGERPRINT.matcher(text(context, "hostKeyFingerprint")).matches()) {
            throw invalid("SFTP 主机指纹格式无效，应使用 SHA256 指纹");
        }
    }

    private String text(ConnectorEndpointValidationContext context, String key) {
        Object value = context.endpointConfig().get(key);
        return value == null ? "" : value.toString().trim();
    }

    private ServiceException invalid(String message) {
        return new ServiceException(GlobalErrorCode.VALIDATION_ERROR, message);
    }
}
