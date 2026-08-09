package com.xfwang.govdata.datasource.infrastructure.connector;

import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectRequest;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretResolveRequest;
import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.api.secret.SecretValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 所有 Connector 能力实现的通用支撑抽象基类。
 *
 * <p>提供统一的凭据解析、端点配置参数提取助手以及耗时计算方法，
 * 不与具体的 Connector Capability 接口（如连通测试或元数据探测）绑定。</p>
 */
public abstract class AbstractConnectorSupport {

    /** 密钥解析服务列表 */
    protected final List<SecretProvider> secretProviders;

    /**
     * 构造函数，注入凭据解析服务。
     *
     * @param secretProviders 凭据解析提供者列表
     */
    protected AbstractConnectorSupport(List<SecretProvider> secretProviders) {
        this.secretProviders = secretProviders;
    }

    /**
     * 解析凭据明文。
     *
     * @param request      凭据解析请求对象
     * @return 包含凭据明文的 Optional，若未找到或解析失败则返回 Optional.empty()
     */
    protected Optional<SecretValue> resolveSecret(SecretResolveRequest request) {
        return secretProviders.stream()
            .map(provider -> provider.resolve(request))
            .flatMap(Optional::stream)
            .findFirst();
    }

    /**
     * 解析连通测试请求中的凭据明文。
     *
     * @param request      连通测试请求
     * @param secretType   凭据类型
     * @param requiredKeys 要求的必填字段集合
     * @return 包含凭据明文的 Optional
     */
    protected Optional<SecretValue> resolveSecret(ConnectionTestRequest request, SecretType secretType, Set<String> requiredKeys) {
        return resolveSecret(request.secretRequest(secretType, requiredKeys));
    }

    /**
     * 解析元数据探测请求中的凭据明文。
     *
     * @param request      元数据探测请求
     * @param secretType   凭据类型
     * @param requiredKeys 要求的必填字段集合
     * @return 包含凭据明文的 Optional
     */
    protected Optional<SecretValue> resolveSecret(MetadataInspectRequest request, SecretType secretType, Set<String> requiredKeys) {
        return resolveSecret(request.secretRequest(secretType, requiredKeys));
    }

    /**
     * 从端点配置中提取指定 Key 的字符串值。
     *
     * @param endpointConfig 端点配置 Map
     * @param key            配置键名
     * @return 字符串值，若不存在或为空则返回空字符串
     */
    protected String text(Map<String, Object> endpointConfig, String key) {
        if (endpointConfig == null) {
            return "";
        }
        Object value = endpointConfig.get(key);
        return value == null ? "" : value.toString().trim();
    }

    /**
     * 从端点配置中提取指定 Key 的整数值。
     *
     * @param endpointConfig 端点配置 Map
     * @param key            配置键名
     * @return 整数值，若不存在或无法解析则返回 0
     */
    protected int integer(Map<String, Object> endpointConfig, String key) {
        if (endpointConfig == null) {
            return 0;
        }
        Object value = endpointConfig.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                // 忽略非数字格式，返回 0
            }
        }
        return 0;
    }

    /**
     * 计算从指定开始时间到当前时间的毫秒耗时。
     *
     * <p>优先当作 {@link System#nanoTime()} 纳秒计算；若传入的是 {@link System#currentTimeMillis()}
     * 毫秒时间戳，则自动兼容计算，始终保证返回非负的毫秒数。</p>
     *
     * @param startedTime 开始时间（支持 nanoTime 或 currentTimeMillis）
     * @return 耗时毫秒数 (>= 0)
     */
    protected long elapsed(long startedTime) {
        if (startedTime <= 0) {
            return 0;
        }
        // 兼容 currentTimeMillis (1.5e12 ~ 4.0e12 区间为 2017~2096 年的毫秒时间戳)
        if (startedTime > 1_500_000_000_000L && startedTime < 4_000_000_000_000L) {
            return Math.max(0, System.currentTimeMillis() - startedTime);
        }
        return Math.max(0, (System.nanoTime() - startedTime) / 1_000_000);
    }
}
