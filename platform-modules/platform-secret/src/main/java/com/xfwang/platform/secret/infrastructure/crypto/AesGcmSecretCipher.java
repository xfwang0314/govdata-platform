package com.xfwang.platform.secret.infrastructure.crypto;

import com.xfwang.platform.secret.domain.exception.SecretErrorCode;
import com.xfwang.platform.secret.infrastructure.config.SecretEncryptionProperties;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class AesGcmSecretCipher {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final SecretEncryptionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plaintext, String associatedData) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                .put(nonce).put(encrypted).array());
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        }
    }

    public String decrypt(String encoded, String associatedData) {
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            if (payload.length <= NONCE_LENGTH) throw new IllegalArgumentException("invalid ciphertext");
            byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, NONCE_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, NONCE_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        }
    }

    public String checksum(String ciphertext) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(ciphertext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String keyVersion() { return properties.getKeyVersion(); }

    private SecretKeySpec key() {
        if (StringUtils.isBlank(properties.getMasterKey())) {
            throw new ServiceException(SecretErrorCode.CONFIGURATION_MISSING);
        }
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(properties.getMasterKey()); }
        catch (IllegalArgumentException ex) { throw new ServiceException(SecretErrorCode.CONFIGURATION_MISSING); }
        if (decoded.length != 32) throw new ServiceException(SecretErrorCode.CONFIGURATION_MISSING);
        return new SecretKeySpec(decoded, "AES");
    }
}
