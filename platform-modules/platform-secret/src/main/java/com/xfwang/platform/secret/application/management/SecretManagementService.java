package com.xfwang.platform.secret.application.management;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfwang.platform.secret.domain.exception.SecretErrorCode;
import com.xfwang.platform.secret.infrastructure.crypto.AesGcmSecretCipher;
import com.xfwang.platform.secret.domain.entity.PlatformSecret;
import com.xfwang.platform.secret.domain.entity.PlatformSecretVersion;
import com.xfwang.platform.secret.domain.enums.SecretEnums;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretMapper;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretVersionMapper;
import com.xfwang.platform.secret.application.management.command.SecretCreateCommand;
import com.xfwang.platform.secret.application.management.query.SecretQuery;
import com.xfwang.platform.secret.application.management.command.SecretRotateCommand;
import com.xfwang.platform.secret.application.management.command.SecretUpdateCommand;
import com.xfwang.platform.secret.application.management.view.SecretView;
import com.xfwang.platform.secret.application.options.SecretUsageCatalog;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SecretManagementService {
    private final PlatformSecretMapper secretMapper;
    private final PlatformSecretVersionMapper versionMapper;
    private final AesGcmSecretCipher cipher;
    private final SecretUsageCatalog usageCatalog;

    public PageResult<SecretView> page(SecretQuery query, PageQuery pageQuery) {
        if (StringUtils.isNotBlank(query.getSecretType())) SecretEnums.type(query.getSecretType());
        if (StringUtils.isNotBlank(query.getStatus())) SecretEnums.status(query.getStatus());
        LambdaQueryWrapper<PlatformSecret> wrapper = new LambdaQueryWrapper<PlatformSecret>()
            .like(StringUtils.isNotBlank(query.getSecretName()), PlatformSecret::getSecretName, query.getSecretName())
            .eq(StringUtils.isNotBlank(query.getSecretType()), PlatformSecret::getSecretType, query.getSecretType())
            .eq(StringUtils.isNotBlank(query.getOwnerModule()), PlatformSecret::getOwnerModule, query.getOwnerModule())
            .eq(StringUtils.isNotBlank(query.getStatus()), PlatformSecret::getStatus, query.getStatus())
            .orderByDesc(PlatformSecret::getCreateTime).orderByDesc(PlatformSecret::getSecretId);
        Page<PlatformSecret> page = secretMapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords().stream().map(this::toView).toList(), page.getTotal());
    }

    public SecretView get(Long secretId) { return toView(require(secretId)); }

    @Transactional(rollbackFor = Exception.class)
    public SecretView create(SecretCreateCommand command) {
        validateValues(command.getSecretType(), command.getValues());
        String purposeScope = usageCatalog.normalizePurposeScope(command.getOwnerModule(), command.getPurposeScope());
        long secretId = IdWorker.getId();
        String secretCode = "sec_" + Long.toUnsignedString(secretId, 36);
        PlatformSecret secret = new PlatformSecret();
        secret.setSecretId(secretId);
        secret.setTenantId(TenantHelper.getTenantId());
        secret.setSecretCode(secretCode);
        secret.setSecretRef("secret:" + secretCode);
        secret.setSecretName(command.getSecretName().trim());
        secret.setDescription(normalizeDescription(command.getDescription()));
        secret.setSecretType(SecretEnums.type(command.getSecretType()));
        secret.setOwnerModule(command.getOwnerModule());
        secret.setPurposeScope(purposeScope);
        secret.setStatus("ACTIVE");
        secret.setExpireTime(command.getExpireTime());
        secret.setLockVersion(0);
        secret.setDelFlag("0");
        secretMapper.insert(secret);
        PlatformSecretVersion version = newVersion(secret, 1, command.getValues(), command.getExpireTime());
        versionMapper.insert(version);
        secret.setCurrentVersionId(version.getSecretVersionId());
        secretMapper.updateById(secret);
        return toView(secret, version.getVersionNo());
    }

    @Transactional(rollbackFor = Exception.class)
    public SecretView rotate(Long secretId, SecretRotateCommand command) {
        PlatformSecret secret = require(secretId);
        if (command.getLockVersion() == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "轮换凭据必须携带 lockVersion");
        }
        validateValues(secret.getSecretType(), command.getValues());
        PlatformSecretVersion current = requireVersion(secret.getCurrentVersionId());
        current.setStatus("DISABLED");
        versionMapper.updateById(current);
        PlatformSecretVersion next = newVersion(secret, current.getVersionNo() + 1,
            command.getValues(), command.getExpireTime());
        versionMapper.insert(next);
        secret.setCurrentVersionId(next.getSecretVersionId());
        secret.setExpireTime(command.getExpireTime());
        secret.setStatus("ACTIVE");
        secret.setLockVersion(command.getLockVersion());
        if (secretMapper.updateById(secret) != 1) {
            throw new ServiceException(GlobalErrorCode.BUSINESS_ERROR, "凭据已被其他请求修改，请刷新后重试");
        }
        return toView(secret, next.getVersionNo());
    }

    @Transactional(rollbackFor = Exception.class)
    public SecretView update(Long secretId, SecretUpdateCommand command) {
        PlatformSecret secret = require(secretId);
        if (command.getLockVersion() == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "修改凭据必须携带 lockVersion");
        }
        String purposeScope = usageCatalog.normalizePurposeScope(secret.getOwnerModule(), command.getPurposeScope());
        secret.setSecretName(command.getSecretName().trim());
        secret.setDescription(normalizeDescription(command.getDescription()));
        secret.setPurposeScope(purposeScope);
        secret.setExpireTime(command.getExpireTime());
        secret.setLockVersion(command.getLockVersion());
        if (secretMapper.updateById(secret) != 1) {
            throw new ServiceException(GlobalErrorCode.BUSINESS_ERROR, "凭据已被其他请求修改，请刷新后重试");
        }
        return toView(secret);
    }

    @Transactional(rollbackFor = Exception.class)
    public SecretView changeStatus(Long secretId, String status) {
        SecretEnums.status(status);
        PlatformSecret secret = require(secretId);
        secret.setStatus(status);
        secretMapper.updateById(secret);
        return toView(secret);
    }

    private PlatformSecretVersion newVersion(PlatformSecret secret, int versionNo,
                                             Map<String, String> values, LocalDateTime expireTime) {
        PlatformSecretVersion version = new PlatformSecretVersion();
        version.setSecretVersionId(IdWorker.getId());
        version.setSecretId(secret.getSecretId());
        version.setVersionNo(versionNo);
        String ciphertext = cipher.encrypt(JsonUtils.toJsonString(values), aad(secret, versionNo));
        version.setCiphertext(ciphertext);
        version.setEncryptionAlgorithm("AES-256-GCM");
        version.setKeyVersion(cipher.keyVersion());
        version.setChecksum(cipher.checksum(ciphertext));
        version.setEffectiveTime(LocalDateTime.now());
        version.setExpireTime(expireTime);
        version.setStatus("ACTIVE");
        return version;
    }

    private String aad(PlatformSecret secret, int versionNo) {
        return secret.getTenantId() + "|" + secret.getSecretId() + "|" + versionNo;
    }

    private void validateValues(String type, Map<String, String> values) {
        SecretEnums.type(type);
        if (values == null || values.isEmpty() || values.size() > 16) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "凭据类型或字段无效");
        }
        if (values.entrySet().stream().anyMatch(e -> StringUtils.isBlank(e.getKey()) || e.getKey().length() > 64
            || StringUtils.isBlank(e.getValue()) || e.getValue().length() > 8192)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "凭据字段名称或值无效");
        }
        if ("USERNAME_PASSWORD".equals(type) && (!values.containsKey("username") || !values.containsKey("password"))) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "用户名密码凭据必须包含 username 和 password");
        }
    }

    private PlatformSecret require(Long id) {
        PlatformSecret secret = id == null ? null : secretMapper.selectById(id);
        if (secret == null) throw new ServiceException(SecretErrorCode.NOT_FOUND);
        return secret;
    }

    private PlatformSecretVersion requireVersion(Long id) {
        PlatformSecretVersion version = id == null ? null : versionMapper.selectById(id);
        if (version == null) throw new ServiceException(SecretErrorCode.UNAVAILABLE);
        return version;
    }

    private SecretView toView(PlatformSecret secret) {
        PlatformSecretVersion version = secret.getCurrentVersionId() == null ? null : versionMapper.selectById(secret.getCurrentVersionId());
        return toView(secret, version == null ? null : version.getVersionNo());
    }

    private SecretView toView(PlatformSecret secret, Integer versionNo) {
        return SecretView.builder().secretId(secret.getSecretId()).secretRef(secret.getSecretRef())
            .secretName(secret.getSecretName()).description(secret.getDescription()).secretType(secret.getSecretType())
            .ownerModule(secret.getOwnerModule())
            .purposeScope(secret.getPurposeScope()).status(secret.getStatus()).expireTime(secret.getExpireTime())
            .currentVersionNo(versionNo).lockVersion(secret.getLockVersion()).createTime(secret.getCreateTime())
            .updateTime(secret.getUpdateTime()).configured(secret.getCurrentVersionId() != null).build();
    }

    private String normalizeDescription(String description) {
        return StringUtils.isBlank(description) ? null : description.trim();
    }
}
