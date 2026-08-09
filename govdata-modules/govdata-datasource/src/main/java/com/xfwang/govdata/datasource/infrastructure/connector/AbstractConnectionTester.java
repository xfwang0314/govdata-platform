package com.xfwang.govdata.datasource.infrastructure.connector;

import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestResult;
import com.xfwang.platform.api.secret.SecretProvider;

import java.util.List;

/**
 * 所有 Connector 连通性测试的抽象基类。
 *
 * <p>继承自 {@link AbstractConnectorSupport}，专门支持 {@link ConnectionTester} 能力。</p>
 */
public abstract class AbstractConnectionTester extends AbstractConnectorSupport implements ConnectionTester {

    /**
     * 构造函数，注入凭据解析服务。
     *
     * @param secretProviders 凭据解析提供者列表
     */
    protected AbstractConnectionTester(List<SecretProvider> secretProviders) {
        super(secretProviders);
    }

    /**
     * 判断当前 Tester 是否支持指定的 Connector 编码和版本。
     *
     * @param connectorCode    Connector 编码
     * @param connectorVersion Connector 版本
     * @return 支持返回 true，否则返回 false
     */
    @Override
    public boolean supports(String connectorCode, String connectorVersion) {
        return connectorCode().equalsIgnoreCase(connectorCode)
            && connectorVersion().equals(connectorVersion);
    }

    /**
     * 获取支持的 Connector 编码。
     *
     * @return Connector 编码
     */
    protected abstract String connectorCode();

    /**
     * 获取支持的 Connector 版本。
     *
     * @return Connector 版本
     */
    protected abstract String connectorVersion();

    /**
     * 从端点配置中提取指定 Key 的字符串值助手。
     */
    protected String text(ConnectionTestRequest request, String key) {
        return text(request.getEndpointConfig(), key);
    }

    /**
     * 从端点配置中提取指定 Key 的整数值助手。
     */
    protected int integer(ConnectionTestRequest request, String key) {
        return integer(request.getEndpointConfig(), key);
    }

    /**
     * 构造测试失败结果。
     *
     * @param reasonCode 原因码
     * @param message    错误描述文本
     * @param latencyMs  连通耗时毫秒
     * @return 测试失败结果对象
     */
    protected ConnectionTestResult failed(String reasonCode, String message, long latencyMs) {
        return ConnectionTestResult.builder()
            .success(false)
            .reasonCode(reasonCode)
            .message(message)
            .latencyMs(latencyMs)
            .build();
    }

    /**
     * 构造测试失败结果。
     *
     * @param reason    原因码枚举
     * @param message    错误描述文本（为 null 时自动使用枚举默认 message）
     * @param latencyMs  连通耗时毫秒
     * @return 测试失败结果对象
     */
    protected ConnectionTestResult failed(ConnectionTestReason reason, String message, long latencyMs) {
        return failed(reason.code(), message == null ? reason.message() : message, latencyMs);
    }
}

