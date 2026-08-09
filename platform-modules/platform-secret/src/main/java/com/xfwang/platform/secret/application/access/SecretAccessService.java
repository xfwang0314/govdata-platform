package com.xfwang.platform.secret.application.access;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.platform.api.secret.SecretCatalog;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretReference;
import com.xfwang.platform.api.secret.SecretReferenceQuery;
import com.xfwang.platform.api.secret.SecretReferenceValidation;
import com.xfwang.platform.api.secret.SecretResolveRequest;
import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.api.secret.SecretValue;
import com.xfwang.platform.secret.domain.entity.PlatformSecret;
import com.xfwang.platform.secret.domain.entity.PlatformSecretAccessLog;
import com.xfwang.platform.secret.domain.entity.PlatformSecretVersion;
import com.xfwang.platform.secret.domain.exception.SecretErrorCode;
import com.xfwang.platform.secret.infrastructure.crypto.SecretCipherFacade;
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

/**
 * 凭据访问与解析服务实现类。
 * <p>
 * 同时实现跨模块契约 {@link SecretProvider}（运行时按需解析凭据明文）和 {@link SecretCatalog}（查询模块可用凭据元数据列表及合法性校验）。
 * 内部严格执行多租户隔离、调用模块匹配、用途范围（PurposeScope）校验、凭据激活状态与过期时间校验，
 * 并在每次解析尝试后追加记录审计日志 {@link PlatformSecretAccessLog}。
 */
@Service
@RequiredArgsConstructor
public class SecretAccessService implements SecretProvider, SecretCatalog {

    private final PlatformSecretMapper secretMapper;
    private final PlatformSecretVersionMapper versionMapper;
    private final PlatformSecretAccessLogMapper accessLogMapper;
    private final SecretCipherFacade cipher;

    /**
     * 根据调用模块与指定用途，查询当前租户下所有可用（ACTIVE且未过期）的凭据引用列表。
     *
     * @param query 凭据引用查询条件
     * @return 匹配的凭据引用列表
     * @throws ServiceException 如果查询条件为空或调用模块/用途缺失
     */
    @Override
    public List<SecretReference> listAvailable(SecretReferenceQuery query) {
        if (query == null || StringUtils.isAnyBlank(query.getCallerModule(), query.getPurpose())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "凭据查询条件不完整");
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<PlatformSecret> queryWrapper = new LambdaQueryWrapper<PlatformSecret>()
                .eq(PlatformSecret::getOwnerModule, query.getCallerModule());
        if (query.getSecretType() != null) {
            queryWrapper.eq(PlatformSecret::getSecretType, query.getSecretType().code());
        }
        return secretMapper.selectList(queryWrapper
                .eq(PlatformSecret::getStatus, "ACTIVE")
                .and(wrapper -> wrapper.isNull(PlatformSecret::getExpireTime)
                    .or().gt(PlatformSecret::getExpireTime, now))
                .orderByAsc(PlatformSecret::getSecretName).orderByAsc(PlatformSecret::getSecretId))
            .stream().filter(secret -> purposeAllowed(secret.getPurposeScope(), query.getPurpose()))
            .map(secret -> SecretReference.builder()
                .secretRef(secret.getSecretRef()).secretName(secret.getSecretName())
                .secretType(requireSecretType(secret.getSecretType())).expireTime(secret.getExpireTime()).build())
            .toList();
    }

    /**
     * 校验指定的凭据引用在当前业务上线文中是否合法且可用于连接。
     *
     * @param validation 校验请求参数
     * @throws ServiceException 如果凭据不存在、已禁用、已过期、模块/类型/用途不匹配或缺少必填字段
     */
    @Override
    public void validateReference(SecretReferenceValidation validation) {
        if (validation == null || validation.getSecretType() == null
            || StringUtils.isAnyBlank(validation.getSecretRef(), validation.getCallerModule(),
                validation.getPurpose())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "凭据引用校验上下文不完整");
        }
        PlatformSecret secret = secretMapper.selectOne(new LambdaQueryWrapper<PlatformSecret>()
            .eq(PlatformSecret::getSecretRef, validation.getSecretRef()));
        if (secret == null || !"ACTIVE".equals(secret.getStatus())
            || (secret.getExpireTime() != null && !secret.getExpireTime().isAfter(LocalDateTime.now()))) {
            throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        }
        if (!validation.getCallerModule().equals(secret.getOwnerModule())
            || !validation.getSecretType().code().equals(secret.getSecretType())
            || !purposeAllowed(secret.getPurposeScope(), validation.getPurpose())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "凭据类型、所属模块或用途与数据源不匹配");
        }
        PlatformSecretVersion version = requireVersion(secret.getCurrentVersionId());
        if (!"ACTIVE".equals(version.getStatus())) {
            throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        }
        String plaintext = cipher.decrypt(version.getEncryptionAlgorithm(), version.getCiphertext(), aad(secret, version.getVersionNo()));
        Map<String, String> values = JsonUtils.parseObject(plaintext, new TypeReference<Map<String, String>>() { });
        if (values == null || !values.keySet().containsAll(validation.getRequiredKeys())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "凭据缺少 Connector 要求的字段");
        }
    }

    /**
     * 运行时按需解析凭据明文。
     * <p>
     * 校验租户隔离、调用模块匹配、用途范围匹配、凭据状态及包含的必填字段后，
     * 使用版本表记录的算法解密得到 {@link SecretValue}。解密操作无论成功失败均写入审计日志。
     *
     * @param request 凭据解析请求参数
     * @return 解析成功时返回包含凭据明文 Map 的 Optional，失败时返回 Optional.empty()
     */
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
            if (request.getExpectedSecretType() != null
                && !request.getExpectedSecretType().code().equals(secret.getSecretType())) {
                reason = "SECRET_TYPE_MISMATCH"; return Optional.empty();
            }
            if (!purposeAllowed(secret.getPurposeScope(), request.getPurpose())) {
                reason = "PURPOSE_NOT_ALLOWED"; return Optional.empty();
            }
            PlatformSecretVersion version = requireVersion(secret.getCurrentVersionId());
            if (!"ACTIVE".equals(version.getStatus())) { reason = "VERSION_DISABLED"; return Optional.empty(); }
            String plaintext = cipher.decrypt(version.getEncryptionAlgorithm(), version.getCiphertext(), aad(secret, version.getVersionNo()));
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

    /** 校验解析请求基础参数格式及多租户跨租户越权防护 */
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

    /** 查询具体的凭据版本记录，若不存在抛出不可用异常 */
    private PlatformSecretVersion requireVersion(Long id) {
        PlatformSecretVersion version = id == null ? null : versionMapper.selectById(id);
        if (version == null) throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        return version;
    }

    /** 转换凭据类型枚举 */
    private SecretType requireSecretType(String code) {
        return SecretType.fromCode(code).orElseThrow(() ->
            new ServiceException(GlobalErrorCode.BUSINESS_ERROR, "凭据类型数据无效"));
    }

    /** 构造 GCM 附加认证数据 AAD：tenantId|secretId|versionNo */
    private String aad(PlatformSecret secret, int versionNo) {
        return secret.getTenantId() + "|" + secret.getSecretId() + "|" + versionNo;
    }

    /** 判断凭据的允许用途范围 PurposeScope 是否包含当前请求的用途编码 */
    private boolean purposeAllowed(String scope, String purpose) {
        return "*".equals(scope) || java.util.Arrays.stream(scope.split(","))
            .map(String::trim).anyMatch(purpose::equals);
    }

    /** 记录凭据解析访问日志（审计），只记录元数据、结果和追踪ID，绝不记录凭据明文 */
    private void audit(PlatformSecret secret, SecretResolveRequest request, String result, String reason) {
        if (request == null) return;
        PlatformSecretAccessLog log = new PlatformSecretAccessLog();
        String tenantId = request.getTenantId();
        if (StringUtils.isBlank(tenantId) && secret != null) {
            tenantId = secret.getTenantId();
        }
        if (StringUtils.isBlank(tenantId)) {
            tenantId = TenantHelper.getTenantId();
        }
        log.setTenantId(tenantId);
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
