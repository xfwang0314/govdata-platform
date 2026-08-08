package com.xfwang.platform.secret.infrastructure.crypto;

import com.xfwang.platform.secret.domain.exception.SecretErrorCode;
import com.xfwang.platform.secret.infrastructure.config.SecretEncryptionProperties;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 基于 AES-256-GCM 模式的凭据对称加解密算法实现类。
 * <p>
 * 采用 12 字节随机 Nonce 与 128 位 GCM 认证标签（Tag），并融合 AAD 附加认证数据，
 * 兼具高强度保密性与防篡改消息认证。
 */
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AesGcmSecretCipher extends AbstractSecretCipher {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造方法。
     *
     * @param properties 凭据全局加密配置
     */
    public AesGcmSecretCipher(SecretEncryptionProperties properties) {
        super(properties);
    }

    /**
     * 返回当前算法的唯一标识名称："AES-256-GCM"。
     *
     * @return "AES-256-GCM"
     */
    @Override
    public String algorithm() {
        return "AES-256-GCM";
    }

    /**
     * 使用 AES-256-GCM 加密明文字符串。
     *
     * @param plaintext      待加密的明文字符串（如 JSON 格式凭据字段）
     * @param associatedData 附加认证数据 AAD（包含租户、凭据ID与版本号）
     * @return Base64 编码的组合密文（前 12 字节为 Nonce，后续为密文+Tag）
     */
    @Override
    public String encrypt(String plaintext, String associatedData) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(decodeMasterKey(), "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                .put(nonce).put(encrypted).array());
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("AES-256-GCM 加密失败", ex);
            throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        }
    }

    /**
     * 使用 AES-256-GCM 解密 Base64 密文字符串。
     *
     * @param encoded        Base64 编码的密文字符串
     * @param associatedData 附加认证数据 AAD，必须与加密时一致
     * @return 解密后的原始明文字符串
     */
    @Override
    public String decrypt(String encoded, String associatedData) {
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            if (payload.length <= NONCE_LENGTH) throw new IllegalArgumentException("invalid ciphertext");
            byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, NONCE_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, NONCE_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decodeMasterKey(), "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("AES-256-GCM 解密失败", ex);
            throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        }
    }
}
