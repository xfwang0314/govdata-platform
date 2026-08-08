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
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;

/**
 * 基于国密 SM4-GCM 模式的凭据对称加解密算法实现类。
 * <p>
 * 依托 BouncyCastle 安全 Provider，使用国密 SM4 分组密码算法（128-bit Key），
 * 搭配 GCM 模式与 AAD 附加数据认证，满足政务信创合规安全性要求。
 */
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class Sm4GcmSecretCipher extends AbstractSecretCipher {

    static {
        // 自动注册 BouncyCastle 国密安全 Provider
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
    }

    /**
     * 构造方法。
     *
     * @param properties 凭据全局加密配置
     */
    public Sm4GcmSecretCipher(SecretEncryptionProperties properties) {
        super(properties);
    }

    /**
     * 返回当前算法的唯一标识名称："SM4-GCM"。
     *
     * @return "SM4-GCM"
     */
    @Override
    public String algorithm() {
        return "SM4-GCM";
    }

    /**
     * 使用国密 SM4-GCM 算法加密明文字符串。
     *
     * @param plaintext      待加密的明文字符串
     * @param associatedData 附加认证数据 AAD
     * @return Base64 编码的密文
     */
    @Override
    public String encrypt(String plaintext, String associatedData) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("SM4/GCM/NoPadding", "BC");
            SecretKeySpec key = new SecretKeySpec(Arrays.copyOf(decodeMasterKey(), 16), "SM4");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array()
            );
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("SM4-GCM 加密失败", ex);
            throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        }
    }

    /**
     * 使用国密 SM4-GCM 算法解密 Base64 密文字符串。
     *
     * @param encoded        Base64 编码的密文字符串
     * @param associatedData 附加认证数据 AAD
     * @return 解密后的原始明文字符串
     */
    @Override
    public String decrypt(String encoded, String associatedData) {
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            byte[] nonce = Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("SM4/GCM/NoPadding", "BC");
            SecretKeySpec key = new SecretKeySpec(Arrays.copyOf(decodeMasterKey(), 16), "SM4");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("SM4-GCM 解密失败", ex);
            throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        }
    }
}
