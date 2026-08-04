package com.xfwang.platform.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.platform.auth.api.enums.PolicyEffect;
import com.xfwang.platform.auth.api.enums.SubjectType;
import com.xfwang.platform.auth.api.model.AuthorizationObligation;
import com.xfwang.platform.auth.api.service.ResourceRegistry;
import com.xfwang.platform.auth.application.model.PolicyDraftCommand;
import com.xfwang.platform.auth.application.model.PolicyResourceCommand;
import com.xfwang.platform.auth.application.model.PolicySubjectCommand;
import com.xfwang.platform.auth.application.model.PolicyView;
import com.xfwang.platform.auth.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.domain.AuthzPolicy;
import com.xfwang.platform.auth.domain.AuthzPolicyAction;
import com.xfwang.platform.auth.domain.AuthzPolicyResource;
import com.xfwang.platform.auth.domain.AuthzPolicySubject;
import com.xfwang.platform.auth.domain.AuthzPolicyVersion;
import com.xfwang.platform.auth.mapper.AuthzPolicyActionMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicyMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicyResourceMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicySubjectMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicyVersionMapper;
import com.xfwang.platform.auth.policy.model.PolicyConditionGroup;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Policy 生命周期管理实现。发布版本及其主体、资源、动作子项发布后不可修改。
 */
@Service
@RequiredArgsConstructor
public class DefaultPolicyAdministrationService implements PolicyAdministrationService {

    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final AuthzPolicyMapper policyMapper;
    private final AuthzPolicyVersionMapper versionMapper;
    private final AuthzPolicySubjectMapper subjectMapper;
    private final AuthzPolicyResourceMapper resourceMapper;
    private final AuthzPolicyActionMapper actionMapper;
    private final AuthorizationJsonCodec jsonCodec;
    private final ResourceRegistry resourceRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PolicyView createDraft(PolicyDraftCommand command) {
        validate(command);
        String code = command.getPolicyCode().trim();
        if (policyMapper.selectCount(new LambdaQueryWrapper<AuthzPolicy>()
            .eq(AuthzPolicy::getPolicyCode, code)) > 0) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "策略编码已存在");
        }

        AuthzPolicy policy = new AuthzPolicy();
        policy.setPolicyCode(code);
        policy.setPolicyName(command.getPolicyName().trim());
        policy.setRiskLevel(normalize(command.getRiskLevel()));
        policy.setStatus("DRAFT");
        policy.setLockVersion(0);
        policy.setDelFlag("0");
        policyMapper.insert(policy);

        AuthzPolicyVersion version = new AuthzPolicyVersion();
        version.setPolicyId(policy.getPolicyId());
        version.setVersionNo(1);
        version.setEffect(normalize(command.getEffect()));
        version.setConditionJson(defaultCondition(command.getConditionJson()));
        version.setObligationJson(command.getObligationJson());
        version.setPriority(command.getPriority() == null ? 0 : command.getPriority());
        version.setVersionStatus("DRAFT");
        version.setContentHash(hash(command));
        version.setCreatedTime(LocalDateTime.now());
        versionMapper.insert(version);

        command.getSubjects().forEach(value -> insertSubject(version.getPolicyVersionId(), value));
        command.getResources().forEach(value -> insertResource(version.getPolicyVersionId(), value));
        command.getActions().stream().map(this::normalize).distinct()
            .forEach(value -> insertAction(version.getPolicyVersionId(), value));
        return toView(policy, version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PolicyView publish(Long policyId) {
        AuthzPolicy policy = requirePolicy(policyId);
        if (!Set.of("DRAFT", "REVIEWING").contains(policy.getStatus())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "只有草稿或审核中的策略可以发布");
        }
        AuthzPolicyVersion version = versionMapper.selectOne(new LambdaQueryWrapper<AuthzPolicyVersion>()
            .eq(AuthzPolicyVersion::getPolicyId, policyId)
            .in(AuthzPolicyVersion::getVersionStatus, List.of("DRAFT", "REVIEWING"))
            .orderByDesc(AuthzPolicyVersion::getVersionNo)
            .last("LIMIT 1"));
        if (version == null) {
            throw new ServiceException(GlobalErrorCode.NOT_FOUND, "没有可发布的策略版本");
        }
        version.setVersionStatus("PUBLISHED");
        version.setPublishedTime(LocalDateTime.now());
        versionMapper.updateById(version);
        policy.setCurrentVersionId(version.getPolicyVersionId());
        policy.setStatus("PUBLISHED");
        policyMapper.updateById(policy);
        return toView(policy, version);
    }

    @Override
    public PolicyView get(Long policyId) {
        AuthzPolicy policy = requirePolicy(policyId);
        AuthzPolicyVersion version = policy.getCurrentVersionId() == null
            ? versionMapper.selectOne(new LambdaQueryWrapper<AuthzPolicyVersion>()
                .eq(AuthzPolicyVersion::getPolicyId, policyId)
                .orderByDesc(AuthzPolicyVersion::getVersionNo)
                .last("LIMIT 1"))
            : versionMapper.selectById(policy.getCurrentVersionId());
        if (version == null) {
            throw new ServiceException(GlobalErrorCode.NOT_FOUND, "策略版本不存在");
        }
        return toView(policy, version);
    }

    private void validate(PolicyDraftCommand command) {
        if (command == null || StringUtils.isBlank(command.getPolicyCode())
            || StringUtils.isBlank(command.getPolicyName())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "策略编码和名称不能为空");
        }
        try {
            PolicyEffect.valueOf(normalize(command.getEffect()));
        } catch (RuntimeException ex) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "effect 只能是 PERMIT 或 DENY");
        }
        if (!RISK_LEVELS.contains(normalize(command.getRiskLevel()))) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "riskLevel 无效");
        }
        if (command.getSubjects() == null || command.getSubjects().isEmpty()
            || command.getResources() == null || command.getResources().isEmpty()
            || command.getActions() == null || command.getActions().isEmpty()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "主体、资源和动作不能为空");
        }
        try {
            jsonCodec.read(defaultCondition(command.getConditionJson()), PolicyConditionGroup.class);
            if (StringUtils.isNotBlank(command.getObligationJson())) {
                jsonCodec.read(command.getObligationJson(), AuthorizationObligation.class);
            }
        } catch (RuntimeException ex) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "策略条件或约束不是合法 JSON");
        }
    }

    private void insertSubject(Long versionId, PolicySubjectCommand command) {
        String type = normalize(command.getSubjectType());
        try {
            SubjectType.valueOf(type);
        } catch (RuntimeException ex) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "主体类型无效");
        }
        String id = "ANY".equals(type) ? "*" : command.getSubjectId();
        if (StringUtils.isBlank(id)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "主体标识不能为空");
        }
        AuthzPolicySubject binding = new AuthzPolicySubject();
        binding.setPolicyVersionId(versionId);
        binding.setSubjectType(type);
        binding.setSubjectId(id);
        binding.setCreateTime(LocalDateTime.now());
        subjectMapper.insert(binding);
    }

    private void insertResource(Long versionId, PolicyResourceCommand command) {
        if (command == null || !command.isSelectorValid()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "resourceId 与 resourceType 必须且只能填写一个");
        }
        if (command.getResourceId() != null && resourceRegistry.findById(command.getResourceId()).isEmpty()) {
            throw new ServiceException(GlobalErrorCode.NOT_FOUND, "策略绑定的授权资源不存在");
        }
        AuthzPolicyResource binding = new AuthzPolicyResource();
        binding.setPolicyVersionId(versionId);
        binding.setResourceId(command.getResourceId());
        binding.setResourceType(StringUtils.isBlank(command.getResourceType()) ? null : command.getResourceType().trim());
        binding.setIncludeChildren(Boolean.TRUE.equals(command.getIncludeChildren()));
        binding.setCreateTime(LocalDateTime.now());
        resourceMapper.insert(binding);
    }

    private void insertAction(Long versionId, String action) {
        if (StringUtils.isBlank(action)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "动作编码不能为空");
        }
        AuthzPolicyAction binding = new AuthzPolicyAction();
        binding.setPolicyVersionId(versionId);
        binding.setActionCode(action);
        binding.setCreateTime(LocalDateTime.now());
        actionMapper.insert(binding);
    }

    private AuthzPolicy requirePolicy(Long policyId) {
        AuthzPolicy policy = policyId == null ? null : policyMapper.selectById(policyId);
        if (policy == null) {
            throw new ServiceException(GlobalErrorCode.NOT_FOUND, "策略不存在");
        }
        return policy;
    }

    private PolicyView toView(AuthzPolicy policy, AuthzPolicyVersion version) {
        return PolicyView.builder()
            .policyId(policy.getPolicyId())
            .policyCode(policy.getPolicyCode())
            .policyName(policy.getPolicyName())
            .policyVersionId(version.getPolicyVersionId())
            .versionNo(version.getVersionNo())
            .effect(version.getEffect())
            .riskLevel(policy.getRiskLevel())
            .status(policy.getStatus())
            .build();
    }

    private String defaultCondition(String conditionJson) {
        return StringUtils.isBlank(conditionJson)
            ? "{\"logic\":\"ALL\",\"conditions\":[],\"groups\":[]}"
            : conditionJson;
    }

    private String hash(PolicyDraftCommand command) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(jsonCodec.write(command).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 不支持 SHA-256", ex);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
