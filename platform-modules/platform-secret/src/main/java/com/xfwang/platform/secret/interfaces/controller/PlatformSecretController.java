package com.xfwang.platform.secret.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.platform.secret.application.management.SecretManagementService;
import com.xfwang.platform.secret.application.management.command.SecretCreateCommand;
import com.xfwang.platform.secret.application.management.command.SecretRotateCommand;
import com.xfwang.platform.secret.application.management.command.SecretUpdateCommand;
import com.xfwang.platform.secret.application.management.query.SecretQuery;
import com.xfwang.platform.secret.application.management.view.SecretView;
import com.xfwang.platform.secret.application.options.SecretOptionsQueryService;
import com.xfwang.platform.secret.application.options.view.SecretOptionsView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 平台凭据管理 REST 控制器。
 * <p>
 * 提供凭据元数据的分页查询、动态下拉字典查询、凭据创建、版本轮换（Rotate）、元数据修改及状态启用/禁用接口。
 * 遵守架构规范：新建和轮换日志注解配置 {@code isSaveRequestData = false, isSaveResponseData = false}，
 * 严禁在 HTTP 日志中暴露任何凭据明文。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/platform/secrets")
public class PlatformSecretController {

    private final SecretManagementService service;
    private final SecretOptionsQueryService optionsQueryService;

    /**
     * 分页查询凭据元数据列表。
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页视图
     */
    @SaCheckPermission("platform:secret:list")
    @GetMapping
    public R<PageResult<SecretView>> page(SecretQuery query, PageQuery pageQuery) {
        return R.ok(service.page(query, pageQuery));
    }

    /**
     * 获取管理端页面所需的动态下拉选项字典数据（凭据类型Schema、状态枚举、拥有模块用途树）。
     *
     * @return 选项字典视图
     */
    @SaCheckPermission("platform:secret:list")
    @GetMapping("/options")
    public R<SecretOptionsView> options() {
        return R.ok(optionsQueryService.options());
    }

    /**
     * 获取单条凭据的元数据详情。
     *
     * @param secretId 凭据主键雪花 ID
     * @return 凭据视图（只包含元数据及配置状态，不包含密码明文）
     */
    @SaCheckPermission("platform:secret:query")
    @GetMapping("/{secretId}")
    public R<SecretView> get(@NotNull @PathVariable Long secretId) {
        return R.ok(service.get(secretId));
    }

    /**
     * 创建凭据。
     * <p>
     * 操作日志强行关闭请求与响应保存，防止明文进入操作日志表。
     *
     * @param command 创建命令
     * @return 刚创建的凭据视图
     */
    @SaCheckPermission("platform:secret:add")
    @Log(title = "平台凭据", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public R<SecretView> create(@Valid @RequestBody SecretCreateCommand command) {
        return R.ok(service.create(command));
    }

    /**
     * 轮换凭据（生成 Version N+1 新密文，并将指针指向新版本）。
     * <p>
     * 操作日志强行关闭请求与响应保存。
     *
     * @param secretId 凭据主键 ID
     * @param command  轮换命令
     * @return 轮换后的凭据视图
     */
    @SaCheckPermission("platform:secret:rotate")
    @Log(title = "轮换平台凭据", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{secretId}/rotate")
    public R<SecretView> rotate(@NotNull @PathVariable Long secretId,
                                @Valid @RequestBody SecretRotateCommand command) {
        return R.ok(service.rotate(secretId, command));
    }

    /**
     * 修改凭据元数据（修改名称、描述说明、用途范围与过期时间）。
     *
     * @param secretId 凭据主键 ID
     * @param command  修改命令
     * @return 修改后的凭据视图
     */
    @SaCheckPermission("platform:secret:edit")
    @Log(title = "修改凭据元数据", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{secretId}")
    public R<SecretView> update(@NotNull @PathVariable Long secretId,
                                @Valid @RequestBody SecretUpdateCommand command) {
        return R.ok(service.update(secretId, command));
    }

    /**
     * 启用凭据（将状态切为 ACTIVE）。
     *
     * @param secretId 凭据主键 ID
     * @return 修改后的凭据视图
     */
    @SaCheckPermission("platform:secret:edit")
    @PutMapping("/{secretId}/enable")
    public R<SecretView> enable(@NotNull @PathVariable Long secretId) {
        return R.ok(service.changeStatus(secretId, "ACTIVE"));
    }

    /**
     * 禁用凭据（将状态切为 DISABLED）。
     *
     * @param secretId 凭据主键 ID
     * @return 修改后的凭据视图
     */
    @SaCheckPermission("platform:secret:edit")
    @PutMapping("/{secretId}/disable")
    public R<SecretView> disable(@NotNull @PathVariable Long secretId) {
        return R.ok(service.changeStatus(secretId, "DISABLED"));
    }
}
