package com.xfwang.platform.secret.api;

import java.util.Optional;

/**
 * 按引用临时解析凭据的稳定平台契约。
 *
 * <p>实现负责审计和生命周期管理；调用方不得记录或返回解析结果。</p>
 */
public interface SecretProvider {

    Optional<SecretValue> resolve(String secretRef, String purpose);
}
