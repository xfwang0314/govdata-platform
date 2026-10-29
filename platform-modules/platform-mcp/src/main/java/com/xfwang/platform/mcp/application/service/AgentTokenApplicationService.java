package com.xfwang.platform.mcp.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfwang.platform.mcp.application.token.command.AgentTokenCreateCommand;
import com.xfwang.platform.mcp.application.token.command.AgentTokenUpdateCommand;
import com.xfwang.platform.mcp.application.token.view.AgentTokenIssuedView;
import com.xfwang.platform.mcp.application.token.view.AgentTokenView;
import com.xfwang.platform.mcp.domain.entity.AuthzAgentToken;
import com.xfwang.platform.mcp.domain.exception.McpErrorCode;
import com.xfwang.platform.mcp.domain.model.AgentTokenStatus;
import com.xfwang.platform.mcp.domain.service.AgentTokenGenerator;
import com.xfwang.platform.mcp.infrastructure.persistence.mapper.AuthzAgentTokenMapper;
import com.xfwang.platform.api.mcp.service.McpToolContributor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.DeptService;
import org.dromara.system.api.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 访问凭据统一管理应用服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTokenApplicationService {

    private final AuthzAgentTokenMapper tokenMapper;
    private final AgentTokenGenerator tokenGenerator;
    private final List<McpToolContributor> toolContributors;

    @Autowired(required = false)
    private UserService userService;

    @Autowired(required = false)
    private DeptService deptService;

    /**
     * 统一分页查询 Agent 凭据列表（管理员可查看全量，普通用户仅查看自建凭据）。
     */
    public PageResult<AgentTokenView> pageTokens(PageQuery pageQuery, String agentName, String status) {
        Long currentUserId = LoginHelper.getUserId();
        boolean hasAdminQueryPerm = LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin();

        LambdaQueryWrapper<AuthzAgentToken> wrapper = new LambdaQueryWrapper<AuthzAgentToken>()
            .like(agentName != null && !agentName.isBlank(), AuthzAgentToken::getAgentName, agentName)
            .eq(status != null && !status.isBlank(), AuthzAgentToken::getStatus, status)
            .eq(!hasAdminQueryPerm, AuthzAgentToken::getUserId, currentUserId)
            .orderByDesc(AuthzAgentToken::getCreateTime);

        Page<AuthzAgentToken> page = tokenMapper.selectPage(pageQuery.build(), wrapper);
        List<AgentTokenView> viewList = toViewList(page.getRecords());

        return PageResult.build(viewList, page.getTotal());
    }

    /**
     * 新建专属 Agent 访问凭据（返回一次性明文 Token）。
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentTokenIssuedView createToken(AgentTokenCreateCommand command, String streamableHttpUrl) {
        Long currentUserId = LoginHelper.getUserId();
        Long currentDeptId = LoginHelper.getDeptId();

        AgentTokenGenerator.GeneratedToken generated = tokenGenerator.generate();
        long tokenId = IdWorker.getId();
        String agentCode = "agt_" + Long.toUnsignedString(tokenId, 36);

        List<String> categories = validateAndNormalize(command.getAllowedCategories(), command.getExpireTime());

        AuthzAgentToken token = new AuthzAgentToken();
        token.setTokenId(tokenId);
        token.setAgentName(command.getAgentName().trim());
        token.setAgentCode(agentCode);
        token.setTokenHash(generated.tokenHash());
        token.setTokenPrefix(generated.tokenPrefix());
        token.setUserId(currentUserId);
        token.setDeptId(currentDeptId);
        token.setAllowedCategories(String.join(",", categories));
        token.setStatus(AgentTokenStatus.ACTIVE.getCode());
        token.setExpireTime(command.getExpireTime());
        token.setMaxDailyRequests(command.getMaxDailyRequests() != null ? command.getMaxDailyRequests() : 2000);
        token.setUsedRequestsToday(0);
        token.setRotationCount(0);
        token.setRemark(command.getRemark());
        token.setCreateTime(LocalDateTime.now());

        tokenMapper.insert(token);

        AgentTokenView view = toView(token);
        // 关联补充用户名与部门名称
        if (userService != null && currentUserId != null) {
            String nick = userService.selectNicknameById(currentUserId);
            view.setUserName(nick != null ? nick : String.valueOf(currentUserId));
        }
        if (deptService != null && currentDeptId != null) {
            String deptName = deptService.selectDeptNameByIds(String.valueOf(currentDeptId));
            view.setDeptName(deptName != null ? deptName : String.valueOf(currentDeptId));
        }
        return buildIssuedView(view, generated.plainToken(), streamableHttpUrl);
    }

    /** 编辑凭据的展示信息、有效期、配额和工具范围。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateToken(Long tokenId, AgentTokenUpdateCommand command) {
        AuthzAgentToken token = getAccessibleToken(tokenId);
        ensureNotRevoked(token);
        List<String> categories = validateAndNormalize(command.getAllowedCategories(), command.getExpireTime());

        int affected = tokenMapper.update(null, Wrappers.<AuthzAgentToken>lambdaUpdate()
            .eq(AuthzAgentToken::getTokenId, tokenId)
            .set(AuthzAgentToken::getAgentName, command.getAgentName().trim())
            .set(AuthzAgentToken::getAllowedCategories, String.join(",", categories))
            .set(AuthzAgentToken::getExpireTime, command.getExpireTime())
            .set(AuthzAgentToken::getMaxDailyRequests,
                command.getMaxDailyRequests() != null ? command.getMaxDailyRequests() : 2000)
            .set(AuthzAgentToken::getRemark, command.getRemark()));
        if (affected != 1) {
            throw new ServiceException(McpErrorCode.TOKEN_CONCURRENT_MODIFICATION);
        }
    }

    /** 轮换 Token；旧 Token 在事务提交后立即失效。 */
    @Transactional(rollbackFor = Exception.class)
    public AgentTokenIssuedView rotateToken(Long tokenId, String streamableHttpUrl) {
        AuthzAgentToken token = getAccessibleToken(tokenId);
        ensureNotRevoked(token);
        AgentTokenGenerator.GeneratedToken generated = tokenGenerator.generate();
        token.setTokenHash(generated.tokenHash());
        token.setTokenPrefix(generated.tokenPrefix());
        token.setLastRotatedTime(LocalDateTime.now());
        token.setRotationCount(token.getRotationCount() == null ? 1 : token.getRotationCount() + 1);
        tokenMapper.updateById(token);
        return buildIssuedView(toView(token), generated.plainToken(), streamableHttpUrl);
    }

    /** 暂停凭据，不改变 Token 和配置。 */
    @Transactional(rollbackFor = Exception.class)
    public void disableToken(Long tokenId) {
        AuthzAgentToken token = getAccessibleToken(tokenId);
        if (AgentTokenStatus.fromCode(token.getStatus()) != AgentTokenStatus.ACTIVE) {
            throw new ServiceException(McpErrorCode.TOKEN_STATUS_TRANSITION_INVALID);
        }
        token.setStatus(AgentTokenStatus.DISABLED.getCode());
        tokenMapper.updateById(token);
    }

    /** 重新启用凭据。 */
    @Transactional(rollbackFor = Exception.class)
    public void enableToken(Long tokenId) {
        AuthzAgentToken token = getAccessibleToken(tokenId);
        if (AgentTokenStatus.fromCode(token.getStatus()) != AgentTokenStatus.DISABLED) {
            throw new ServiceException(McpErrorCode.TOKEN_STATUS_TRANSITION_INVALID);
        }
        if (token.getExpireTime() != null && !token.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new ServiceException(McpErrorCode.TOKEN_EXPIRED);
        }
        token.setStatus(AgentTokenStatus.ACTIVE.getCode());
        tokenMapper.updateById(token);
    }

    private AgentTokenIssuedView buildIssuedView(AgentTokenView view, String plainToken, String streamableHttpUrl) {
        return AgentTokenIssuedView.builder()
            .token(view)
            .plainToken(plainToken)
            .streamableHttpUrl(streamableHttpUrl)
            .clientConfigJson(tokenGenerator.formatClientConfig(streamableHttpUrl, plainToken))
            .build();
    }

    /**
     * 吊销指定的 Agent 凭据。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeToken(Long tokenId) {
        AuthzAgentToken token = getAccessibleToken(tokenId);
        ensureNotRevoked(token);
        token.setStatus(AgentTokenStatus.REVOKED.getCode());
        tokenMapper.updateById(token);
    }

    /**
     * 删除指定的 Agent 凭据（仅允许删除已处于吊销状态的凭据）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteToken(Long tokenId) {
        AuthzAgentToken token = getAccessibleToken(tokenId);
        if (AgentTokenStatus.fromCode(token.getStatus()) != AgentTokenStatus.REVOKED) {
            throw new ServiceException(McpErrorCode.TOKEN_STATUS_TRANSITION_INVALID);
        }
        tokenMapper.deleteById(tokenId);
    }

    private void ensureNotRevoked(AuthzAgentToken token) {
        if (AgentTokenStatus.fromCode(token.getStatus()) == AgentTokenStatus.REVOKED) {
            throw new ServiceException(McpErrorCode.TOKEN_REVOKED);
        }
    }

    private List<String> validateAndNormalize(List<String> requestedCategories, LocalDateTime expireTime) {
        List<String> categories = requestedCategories != null && !requestedCategories.isEmpty()
            ? requestedCategories.stream().distinct().sorted().toList()
            : List.of("DATASOURCE");
        java.util.Set<String> availableCategories = toolContributors.stream()
            .map(contributor -> contributor.module().code())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!availableCategories.containsAll(categories)) {
            throw new ServiceException(McpErrorCode.MODULE_CODE_INVALID);
        }
        if (expireTime != null && !expireTime.isAfter(LocalDateTime.now())) {
            throw new ServiceException(McpErrorCode.TOKEN_EXPIRE_TIME_INVALID);
        }
        return categories;
    }

    private AuthzAgentToken getAccessibleToken(Long tokenId) {
        AuthzAgentToken token = tokenMapper.selectById(tokenId);
        if (token == null) {
            throw new ServiceException(McpErrorCode.TOKEN_NOT_FOUND);
        }
        Long currentUserId = LoginHelper.getUserId();
        if (!currentUserId.equals(token.getUserId()) && !LoginHelper.isSuperAdmin() && !LoginHelper.isTenantAdmin()) {
            throw new ServiceException(McpErrorCode.TOKEN_ACCESS_DENIED);
        }
        return token;
    }

    private List<AgentTokenView> toViewList(List<AuthzAgentToken> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = records.stream().map(AuthzAgentToken::getUserId).filter(Objects::nonNull).distinct().toList();
        List<Long> deptIds = records.stream().map(AuthzAgentToken::getDeptId).filter(Objects::nonNull).distinct().toList();

        Map<Long, String> userNames = (userService != null && !userIds.isEmpty())
            ? userService.selectUserNicksByIds(userIds)
            : Collections.emptyMap();

        Map<Long, String> deptNames = (deptService != null && !deptIds.isEmpty())
            ? deptService.selectDeptNamesByIds(deptIds)
            : Collections.emptyMap();

        return records.stream()
            .map(entity -> {
                AgentTokenView view = toView(entity);
                if (entity.getUserId() != null) {
                    view.setUserName(userNames.getOrDefault(entity.getUserId(), String.valueOf(entity.getUserId())));
                }
                if (entity.getDeptId() != null) {
                    view.setDeptName(deptNames.getOrDefault(entity.getDeptId(), String.valueOf(entity.getDeptId())));
                }
                return view;
            })
            .toList();
    }

    private AgentTokenView toView(AuthzAgentToken entity) {
        List<String> categoryList = entity.getAllowedCategories() != null && !entity.getAllowedCategories().isBlank()
            ? Arrays.stream(entity.getAllowedCategories().split(",")).map(String::trim).toList()
            : List.of("DATASOURCE");

        return AgentTokenView.builder()
            .tokenId(entity.getTokenId())
            .agentName(entity.getAgentName())
            .agentCode(entity.getAgentCode())
            .tokenPrefix(entity.getTokenPrefix())
            .userId(entity.getUserId())
            .deptId(entity.getDeptId())
            .allowedCategories(categoryList)
            .status(entity.getStatus())
            .expireTime(entity.getExpireTime())
            .maxDailyRequests(entity.getMaxDailyRequests())
            .usedRequestsToday(entity.getUsedRequestsToday())
            .lastUsedTime(entity.getLastUsedTime())
            .lastRotatedTime(entity.getLastRotatedTime())
            .rotationCount(entity.getRotationCount())
            .remark(entity.getRemark())
            .createTime(entity.getCreateTime())
            .build();
    }
}
