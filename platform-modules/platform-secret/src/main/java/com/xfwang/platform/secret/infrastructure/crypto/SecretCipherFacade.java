package com.xfwang.platform.secret.infrastructure.crypto;

import com.xfwang.platform.secret.domain.exception.SecretErrorCode;
import com.xfwang.platform.secret.infrastructure.config.SecretEncryptionProperties;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 凭据加解密统一门面与策略路由组件。
 * <p>
 * 遵循“写入按当前配置算法，解密按版本记录算法路由”原则，
 * 集中管理系统内所有的 {@link SecretCipher} 实现类（如 AES-256-GCM、SM4-GCM）。
 */
@Component
public class SecretCipherFacade {
    private final SecretEncryptionProperties properties;
    private final Map<String, SecretCipher> cipherMap;

    /**
     * 构造方法：注入加解密配置，并自动收集所有的 {@link SecretCipher} 算法实现类构建路由 Map。
     *
     * @param properties 凭据加密全局配置
     * @param ciphers    系统中所有已注册的加解密算法实现类列表
     */
    public SecretCipherFacade(SecretEncryptionProperties properties, List<SecretCipher> ciphers) {
        this.properties = properties;
        this.cipherMap = ciphers.stream().collect(Collectors.toMap(SecretCipher::algorithm, c -> c));
    }

    /**
     * 获取当前系统配置文件中指定的默认加密算法名称（如 "AES-256-GCM" 或 "SM4-GCM"）。
     *
     * @return 当前默认算法编码
     */
    public String currentAlgorithm() {
        return normalizeAlgorithm(properties.getAlgorithm());
    }

    /**
     * 使用当前系统默认配置的算法加密凭据 JSON 字符串。
     *
     * @param jsonString 明文 JSON 字符串
     * @param aad        附加认证数据（Associated Authenticated Data），格式通常为 "tenantId|secretId|versionNo"
     * @return Base64 编码的密文
     */
    public String encrypt(String jsonString, String aad) {
        return getCipher(currentAlgorithm()).encrypt(jsonString, aad);
    }

    /**
     * 根据指定的算法名称动态路由解密密文（主要用于按数据库版本表中记录的加密算法解密历史或不同算法的数据）。
     *
     * @param algorithm  算法名称，如 "AES-256-GCM" 或 "SM4-GCM"
     * @param ciphertext Base64 编码的密文
     * @param aad        附加认证数据（Associated Authenticated Data）
     * @return 解密后的明文字符串
     */
    public String decrypt(String algorithm, String ciphertext, String aad) {
        return getCipher(algorithm).decrypt(ciphertext, aad);
    }

    /**
     * 计算密文的 SHA-256 校验和摘要。
     *
     * @param ciphertext 密文
     * @return 64 位十六进制 SHA-256 校验和
     */
    public String checksum(String ciphertext) {
        return getCipher(currentAlgorithm()).checksum(ciphertext);
    }

    /**
     * 获取当前配置的主密钥版本标识（如 "v1"、"v2"）。
     *
     * @return 主密钥版本字符串
     */
    public String keyVersion() {
        return properties.getKeyVersion();
    }

    /**
     * 根据算法名称从路由 Map 中获取具体的加解密实现类，不存在时抛出不可用异常。
     *
     * @param algorithm 算法名称
     * @return 对应的加解密实现类
     */
    private SecretCipher getCipher(String algorithm) {
        String normalized = normalizeAlgorithm(algorithm);
        SecretCipher cipher = cipherMap.get(normalized);
        if (cipher == null) {
            throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        }
        return cipher;
    }

    private String normalizeAlgorithm(String algorithm) {
        if (algorithm == null) return null;
        return algorithm.replace("\"", "").replace("'", "").trim();
    }
}
