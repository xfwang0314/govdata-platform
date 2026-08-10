package com.xfwang.platform.secret.application.management;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.secret.application.management.command.SecretCreateCommand;
import com.xfwang.platform.secret.application.management.command.SecretRotateCommand;
import com.xfwang.platform.secret.application.management.command.SecretUpdateCommand;
import com.xfwang.platform.secret.application.management.query.SecretQuery;
import com.xfwang.platform.secret.application.management.view.SecretView;
import com.xfwang.platform.secret.application.options.SecretUsageCatalog;
import com.xfwang.platform.secret.domain.entity.PlatformSecret;
import com.xfwang.platform.secret.domain.entity.PlatformSecretVersion;
import com.xfwang.platform.secret.domain.enums.SecretEnums;
import com.xfwang.platform.secret.domain.exception.SecretErrorCode;
import com.xfwang.platform.secret.infrastructure.crypto.SecretCipherFacade;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretMapper;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretVersionMapper;
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
import java.util.regex.Pattern;

/**
 * 凭据管理服务应用层实现类。
 * <p>
 * 负责凭据元数据与加密密文版本的全生命周期管理，
 * 包括凭据的分页查询、详情获取、新建、凭据轮换（Rotate）、元数据修改及状态切换（启用/禁用）。
 * 遵循只在创建/轮换时接收一次明文、后端持久化加密、响应结果中绝不回显任何敏感明文的安全原则。
 */
@Service
@RequiredArgsConstructor
public class SecretManagementService {

    private static final Pattern DYNAMIC_FIELD_CODE = Pattern.compile("^[a-z][a-zA-Z0-9_]{0,63}$");
    private final PlatformSecretMapper secretMapper;
    private final PlatformSecretVersionMapper versionMapper;
    private final SecretCipherFacade cipher;
    private final SecretUsageCatalog usageCatalog;

    /**
     * 分页查询当前租户下的凭据元数据列表。
     *
     * @param query     凭据筛选查询条件（凭据名称、类型、所属模块、状态等）
     * @param pageQuery 分页参数（页码、页大小）
     * @return 分页视图结果
     */
    public PageResult<SecretView> page(SecretQuery query, PageQuery pageQuery) {
        if (StringUtils.isNotBlank(query.getSecretType())) requireSecretType(query.getSecretType());
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

    /**
     * 获取单条凭据的详情视图（只包含元数据，configured 标记是否配置了密文，不包含敏感明文）。
     *
     * @param secretId 凭据主键雪花 ID
     * @return 凭据视图对象
     */
    public SecretView get(Long secretId) {
        return toView(require(secretId));
    }

    /**
     * 创建新凭据。
     * <p>
     * 自动生成 36 进制全局唯一 {@code secretCode} 和稳定的 {@code secretRef}（如 {@code secret:sec_xxx}）。
     * 校验上传的凭据键值字段是否合法，并使用当前默认算法加密生成 Version 1 密文记录。
     *
     * @param command 创建命令
     * @return 刚创建的凭据视图对象
     */
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
        secret.setSecretType(requireSecretType(command.getSecretType()).code());
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

    /**
     * 执行凭据版本轮换（Rotate）。
     * <p>
     * 保持 {@code secretRef} 不变，插入递增版本号（Version N+1）的新密文记录并设为当前激活版本，
     * 同时将上一版本的状态设为 {@code DISABLED}，实现零中断平滑轮换。
     *
     * @param secretId 凭据主键 ID
     * @param command  轮换命令（需携带当前乐观锁版本 lockVersion 和新凭据明文 Map）
     * @return 轮换后的凭据视图对象
     */
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

    /**
     * 更新凭据元数据（修改凭据名称、描述说明、允许用途范围及过期时间）。
     *
     * @param secretId 凭据主键 ID
     * @param command  修改命令（包含乐观锁版本 lockVersion）
     * @return 修改后的凭据视图
     */
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

    /**
     * 切换凭据的状态（启用 ACTIVE / 禁用 DISABLED）。
     *
     * @param secretId 凭据主键 ID
     * @param status   目标状态编码
     * @return 修改后的凭据视图
     */
    @Transactional(rollbackFor = Exception.class)
    public SecretView changeStatus(Long secretId, String status) {
        SecretEnums.status(status);
        PlatformSecret secret = require(secretId);
        secret.setStatus(status);
        secretMapper.updateById(secret);
        return toView(secret);
    }

    /** 构建新的凭据版本实体并使用全局配置的当前默认算法加密密文 */
    private PlatformSecretVersion newVersion(PlatformSecret secret, int versionNo,
                                             Map<String, String> values, LocalDateTime expireTime) {
        PlatformSecretVersion version = new PlatformSecretVersion();
        version.setSecretVersionId(IdWorker.getId());
        version.setSecretId(secret.getSecretId());
        version.setVersionNo(versionNo);
        String ciphertext = cipher.encrypt(JsonUtils.toJsonString(values), aad(secret, versionNo));
        version.setCiphertext(ciphertext);
        version.setEncryptionAlgorithm(cipher.currentAlgorithm());
        version.setKeyVersion(cipher.keyVersion());
        version.setChecksum(cipher.checksum(ciphertext));
        version.setEffectiveTime(LocalDateTime.now());
        version.setExpireTime(expireTime);
        version.setStatus("ACTIVE");
        return version;
    }

    /** 构造附加认证数据 AAD：tenantId|secretId|versionNo */
    private String aad(PlatformSecret secret, int versionNo) {
        return secret.getTenantId() + "|" + secret.getSecretId() + "|" + versionNo;
    }

    /** 校验前端提交的凭据字段 Map 是否满足该 SecretType 的 Schema 约束（必填键、允许键、长度限制等） */
    private void validateValues(String type, Map<String, String> values) {
        SecretType secretType = requireSecretType(type);
        if (values == null || values.isEmpty() || values.size() > 16) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "凭据类型或字段无效");
        }
        if (values.entrySet().stream().anyMatch(e -> StringUtils.isBlank(e.getKey())
            || !DYNAMIC_FIELD_CODE.matcher(e.getKey()).matches()
            || StringUtils.isBlank(e.getValue()))) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "凭据字段名称或值无效");
        }
        if (!values.keySet().containsAll(secretType.requiredKeys())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                secretType.label() + "必须包含字段: " + String.join(",", secretType.requiredKeys()));
        }
        if (!secretType.dynamicFieldsAllowed() && !secretType.allowedKeys().containsAll(values.keySet())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, secretType.label() + "包含未定义字段");
        }
        secretType.fields().forEach(field -> {
            String value = values.get(field.code());
            if (value != null && value.length() > field.maxLength()) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, field.label() + "长度超出允许范围");
            }
        });
        if (secretType.dynamicFieldsAllowed()
            && values.values().stream().anyMatch(value -> value.length() > 8192)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "凭据字段值长度超出允许范围");
        }
    }

    private SecretType requireSecretType(String code) {
        return SecretType.fromCode(code).orElseThrow(() ->
            new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "凭据类型无效"));
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
