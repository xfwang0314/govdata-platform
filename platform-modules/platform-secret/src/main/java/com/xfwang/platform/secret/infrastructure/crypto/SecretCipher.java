package com.xfwang.platform.secret.infrastructure.crypto;

/**
 * 凭据对称加解密算法统一抽象接口。
 * <p>
 * 定义了平台内各种具体加密算法（如 AES-256-GCM、SM4-GCM）必须实现的通用能力规范，
 * 包含对称加密、对称解密、密文校验和计算以及获取密钥版本信息。
 */
public interface SecretCipher {

    /**
     * 获取算法唯一标识名称。
     *
     * @return 算法名称字符串，如 "AES-256-GCM"、"SM4-GCM"
     */
    String algorithm();

    /**
     * 对明文字符串进行对称加密。
     *
     * @param plaintext      待加密的明文字符串
     * @param associatedData 附加认证数据（AAD），用于防篡改校验，格式如 "tenantId|secretId|versionNo"
     * @return Base64 编码后的密文字符串
     */
    String encrypt(String plaintext, String associatedData);

    /**
     * 对密文字符串进行对称解密。
     *
     * @param ciphertext     Base64 编码的密文字符串
     * @param associatedData 附加认证数据（AAD），必须与加密时传入的值完全一致
     * @return 解密后的原始明文字符串
     */
    String decrypt(String ciphertext, String associatedData);

    /**
     * 计算密文内容的 64 位十六进制 SHA-256 校验和摘要。
     *
     * @param ciphertext 密文字符串
     * @return SHA-256 校验和值
     */
    String checksum(String ciphertext);

    /**
     * 获取当前加密所使用的主密钥版本标识（如 "v1"、"v2"）。
     *
     * @return 主密钥版本号
     */
    String keyVersion();
}
