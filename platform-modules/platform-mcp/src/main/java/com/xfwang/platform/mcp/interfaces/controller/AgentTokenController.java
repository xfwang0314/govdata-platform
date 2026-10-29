package com.xfwang.platform.mcp.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.platform.mcp.application.service.AgentTokenApplicationService;
import com.xfwang.platform.mcp.application.token.command.AgentTokenCreateCommand;
import com.xfwang.platform.mcp.application.token.command.AgentTokenUpdateCommand;
import com.xfwang.platform.mcp.application.token.view.AgentTokenIssuedView;
import com.xfwang.platform.mcp.application.token.view.AgentTokenView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 访问凭据统一管理控制器。
 */
@Validated
@RestController
@RequestMapping("/platform/mcp/tokens")
@RequiredArgsConstructor
public class AgentTokenController {

    private final AgentTokenApplicationService tokenService;

    /**
     * 分页查询 Agent 凭据列表。
     */
    @SaCheckPermission("platform:mcp:agent:query")
    @GetMapping("/page")
    public R<PageResult<AgentTokenView>> page(
        PageQuery pageQuery,
        @RequestParam(required = false) String agentName,
        @RequestParam(required = false) String status
    ) {
        return R.ok(tokenService.pageTokens(pageQuery, agentName, status));
    }

    /**
     * 新建专属 Agent 访问凭据。
     */
    @SaCheckPermission("platform:mcp:agent:add")
    @Log(title = "新建Agent凭据", businessType = BusinessType.INSERT)
    @PostMapping
    public R<AgentTokenIssuedView> create(@Valid @RequestBody AgentTokenCreateCommand command,
                                           HttpServletRequest request) {
        return R.ok(tokenService.createToken(command, resolveStreamableHttpUrl(request)));
    }

    @SaCheckPermission("platform:mcp:agent:edit")
    @Log(title = "编辑Agent凭据", businessType = BusinessType.UPDATE)
    @PutMapping("/{tokenId}")
    public R<Void> update(@PathVariable Long tokenId,
                          @Valid @RequestBody AgentTokenUpdateCommand command) {
        tokenService.updateToken(tokenId, command);
        return R.ok();
    }

    @SaCheckPermission("platform:mcp:agent:rotate")
    @Log(title = "轮换Agent凭据", businessType = BusinessType.UPDATE)
    @PostMapping("/{tokenId}/rotate")
    public R<AgentTokenIssuedView> rotate(@PathVariable Long tokenId, HttpServletRequest request) {
        return R.ok(tokenService.rotateToken(tokenId, resolveStreamableHttpUrl(request)));
    }

    @SaCheckPermission("platform:mcp:agent:status")
    @Log(title = "停用Agent凭据", businessType = BusinessType.UPDATE)
    @PutMapping("/{tokenId}/disable")
    public R<Void> disable(@PathVariable Long tokenId) {
        tokenService.disableToken(tokenId);
        return R.ok();
    }

    @SaCheckPermission("platform:mcp:agent:status")
    @Log(title = "启用Agent凭据", businessType = BusinessType.UPDATE)
    @PutMapping("/{tokenId}/enable")
    public R<Void> enable(@PathVariable Long tokenId) {
        tokenService.enableToken(tokenId);
        return R.ok();
    }

    /**
     * 吊销指定的 Agent 访问凭据。
     */
    @SaCheckPermission("platform:mcp:agent:revoke")
    @Log(title = "吊销Agent凭据", businessType = BusinessType.UPDATE)
    @PutMapping("/{tokenId}/revoke")
    public R<Void> revoke(@PathVariable Long tokenId) {
        tokenService.revokeToken(tokenId);
        return R.ok();
    }

    /**
     * 删除指定的 Agent 访问凭据（支持删除已吊销凭据）。
     */
    @SaCheckPermission("platform:mcp:agent:delete")
    @Log(title = "删除Agent凭据", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tokenId}")
    public R<Void> delete(@PathVariable Long tokenId) {
        tokenService.deleteToken(tokenId);
        return R.ok();
    }

    private String resolveStreamableHttpUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        int serverPort = request.getServerPort();
        String portPart = (("http".equals(scheme) && serverPort == 80)
            || ("https".equals(scheme) && serverPort == 443)) ? "" : ":" + serverPort;
        return scheme + "://" + request.getServerName() + portPart
            + request.getContextPath() + "/platform/mcp";
    }
}
