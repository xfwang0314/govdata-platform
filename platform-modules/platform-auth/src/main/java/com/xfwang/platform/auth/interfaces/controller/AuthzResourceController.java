package com.xfwang.platform.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resource 管理入口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/platform/auth/resources")
public class AuthzResourceController {

    private final ResourceRegistry resourceRegistry;

    @SaCheckPermission("platform:auth:resource:add")
    @Log(title = "授权资源", businessType = BusinessType.INSERT)
    @PostMapping
    public R<ResourceDescriptor> register(@RequestBody ResourceDescriptor descriptor) {
        return R.ok(resourceRegistry.register(descriptor));
    }

    @SaCheckPermission("platform:auth:resource:query")
    @GetMapping("/{resourceId}")
    public R<ResourceDescriptor> get(@PathVariable Long resourceId) {
        return R.ok(resourceRegistry.findById(resourceId)
            .orElseThrow(() -> new ServiceException(GlobalErrorCode.NOT_FOUND, "授权资源不存在")));
    }
}
