package com.xfwang.platform.secret.infrastructure.crypto;

import com.xfwang.platform.secret.domain.exception.SecretErrorCode;
import com.xfwang.platform.secret.infrastructure.config.SecretEncryptionProperties;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 凭据对称加解密算法抽象基类。
 * <p>
 * 封装了从配置对象读取/解密 32 字节 Base64 主密钥、获取主密钥版本号
 * 以及计算 SHA-256 密文校验和摘要等通用基础设施逻辑。
 */
public abstract class AbstractSecretCipher implements SecretCipher {

    protected final SecretEncryptionProperties properties;

    /**
     * 构造函数。
     *
     * @param properties 凭据全局加密配置对象
     */
    public AbstractSecretCipher(SecretEncryptionProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验并返回 Base64 解码后的 32 字节原始主密钥。
     * <p>
     * 子类可以根据各自算法（如 AES-256 需要 32 字节，SM4-128 需要前 16 字节）构造 {@link javax.crypto.spec.SecretKeySpec}。
     *
     * @return 32 字节解密后的主密钥字节数组
     * @throws ServiceException 如果主密钥配置缺失或无效，抛出 {@link SecretErrorCode#CONFIGURATION_MISSING}
     */
    protected byte[] decodeMasterKey() {
        if (StringUtils.isBlank(properties.getMasterKey())) {
            throw new ServiceException(SecretErrorCode.CONFIGURATION_MISSING);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.getMasterKey());
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(SecretErrorCode.CONFIGURATION_MISSING);
        }
        if (decoded.length != 32) {
            throw new ServiceException(SecretErrorCode.CONFIGURATION_MISSING);
        }
        return decoded;
    }

    /**
     * 获取当前全局配置的主密钥版本标识（如 "v1"、"v2"）。
     *
     * @return 密钥版本号
     */
    @Override
    public String keyVersion() {
        return properties.getKeyVersion();
    }

    /**
     * 计算密文内容的 64 位十六进制 SHA-256 校验和摘要，用于完整性比对。
     *
     * @param ciphertext 密文字符串
     * @return 64 位十六进制字符串摘要
     */
    @Override
    public String checksum(String ciphertext) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(ciphertext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
