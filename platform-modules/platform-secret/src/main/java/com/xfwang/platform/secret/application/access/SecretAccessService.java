package com.xfwang.platform.secret.application.access;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.platform.api.secret.SecretCatalog;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretReference;
import com.xfwang.platform.api.secret.SecretResolveRequest;
import com.xfwang.platform.api.secret.SecretValue;
import com.xfwang.platform.secret.domain.entity.PlatformSecret;
import com.xfwang.platform.secret.domain.entity.PlatformSecretAccessLog;
import com.xfwang.platform.secret.domain.entity.PlatformSecretVersion;
import com.xfwang.platform.secret.domain.exception.SecretErrorCode;
import com.xfwang.platform.secret.infrastructure.crypto.AesGcmSecretCipher;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretAccessLogMapper;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretMapper;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretVersionMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SecretAccessService implements SecretProvider, SecretCatalog {

    private final PlatformSecretMapper secretMapper;
    private final PlatformSecretVersionMapper versionMapper;
    private final PlatformSecretAccessLogMapper accessLogMapper;
    private final AesGcmSecretCipher cipher;

    @Override
    public List<SecretReference> listAvailable(String callerModule, String secretType) {
        if (StringUtils.isBlank(callerModule)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "调用模块不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        return secretMapper.selectList(new LambdaQueryWrapper<PlatformSecret>()
                .eq(PlatformSecret::getOwnerModule, callerModule)
                .eq(StringUtils.isNotBlank(secretType), PlatformSecret::getSecretType, secretType)
                .eq(PlatformSecret::getStatus, "ACTIVE")
                .and(wrapper -> wrapper.isNull(PlatformSecret::getExpireTime)
                    .or().gt(PlatformSecret::getExpireTime, now))
                .orderByAsc(PlatformSecret::getSecretName).orderByAsc(PlatformSecret::getSecretId))
            .stream().map(secret -> SecretReference.builder()
                .secretRef(secret.getSecretRef()).secretName(secret.getSecretName())
                .secretType(secret.getSecretType()).expireTime(secret.getExpireTime()).build())
            .toList();
    }

    @Override
    public Optional<SecretValue> resolve(SecretResolveRequest request) {
        if (request == null || request.getSecretRef() == null || !request.getSecretRef().startsWith("secret:")) {
            return Optional.empty();
        }
        PlatformSecret secret = null;
        String reason = "SECRET_NOT_FOUND";
        try {
            validateRequest(request);
            secret = secretMapper.selectOne(new LambdaQueryWrapper<PlatformSecret>()
                .eq(PlatformSecret::getSecretRef, request.getSecretRef()));
            if (secret == null) return Optional.empty();
            if (!request.getTenantId().equals(secret.getTenantId())) { reason = "TENANT_MISMATCH"; return Optional.empty(); }
            if (!"ACTIVE".equals(secret.getStatus())) { reason = "SECRET_DISABLED"; return Optional.empty(); }
            if (secret.getExpireTime() != null && !secret.getExpireTime().isAfter(LocalDateTime.now())) {
                reason = "SECRET_EXPIRED"; return Optional.empty();
            }
            if (!secret.getOwnerModule().equals(request.getCallerModule())) {
                reason = "CALLER_NOT_ALLOWED"; return Optional.empty();
            }
            if (!purposeAllowed(secret.getPurposeScope(), request.getPurpose())) {
                reason = "PURPOSE_NOT_ALLOWED"; return Optional.empty();
            }
            PlatformSecretVersion version = requireVersion(secret.getCurrentVersionId());
            if (!"ACTIVE".equals(version.getStatus())) { reason = "VERSION_DISABLED"; return Optional.empty(); }
            String plaintext = cipher.decrypt(version.getCiphertext(), aad(secret, version.getVersionNo()));
            Map<String, String> values = JsonUtils.parseObject(plaintext, new TypeReference<Map<String, String>>() { });
            if (values == null || !values.keySet().containsAll(request.getRequiredKeys())) {
                reason = "REQUIRED_KEYS_MISSING"; return Optional.empty();
            }
            Map<String, char[]> protectedValues = new HashMap<>();
            values.forEach((key, value) -> protectedValues.put(key, value == null ? null : value.toCharArray()));
            audit(secret, request, "SUCCESS", "RESOLVED");
            reason = "RESOLVED";
            return Optional.of(new SecretValue(protectedValues));
        } catch (ServiceException ex) {
            reason = ex.getErrorCode() == null ? "RESOLVE_FAILED" : ex.getErrorCode();
            return Optional.empty();
        } finally {
            if (secret == null || !"RESOLVED".equals(reason)) {
                audit(secret, request, "FAILED", reason);
            }
        }
    }

    private void validateRequest(SecretResolveRequest request) {
        if (StringUtils.isAnyBlank(request.getTenantId(), request.getSecretRef(), request.getCallerModule(),
            request.getActorType(), request.getActorId(), request.getPurpose())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "凭据解析上下文不完整");
        }
        String currentTenant = TenantHelper.getTenantId();
        if (StringUtils.isNotBlank(currentTenant) && !currentTenant.equals(request.getTenantId())) {
            throw new ServiceException(GlobalErrorCode.FORBIDDEN, "禁止跨租户解析凭据");
        }
    }

    private PlatformSecretVersion requireVersion(Long id) {
        PlatformSecretVersion version = id == null ? null : versionMapper.selectById(id);
        if (version == null) throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        return version;
    }

    private String aad(PlatformSecret secret, int versionNo) {
        return secret.getTenantId() + "|" + secret.getSecretId() + "|" + versionNo;
    }

    private boolean purposeAllowed(String scope, String purpose) {
        return "*".equals(scope) || java.util.Arrays.stream(scope.split(","))
            .map(String::trim).anyMatch(purpose::equals);
    }

    private void audit(PlatformSecret secret, SecretResolveRequest request, String result, String reason) {
        if (request == null) return;
        PlatformSecretAccessLog log = new PlatformSecretAccessLog();
        log.setSecretId(secret == null ? null : secret.getSecretId());
        log.setSecretRefSnapshot(request.getSecretRef());
        log.setCallerModule(request.getCallerModule());
        log.setActorType(request.getActorType());
        log.setActorId(request.getActorId());
        log.setPurpose(request.getPurpose());
        log.setResult(result);
        log.setReasonCode(reason);
        log.setTraceId(request.getTraceId());
        log.setOccurredTime(LocalDateTime.now());
        accessLogMapper.insert(log);
    }
}
