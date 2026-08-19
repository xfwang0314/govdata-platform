package com.xfwang.platform.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import com.xfwang.platform.auth.application.resource.ResourceQueryService;
import com.xfwang.platform.auth.application.resource.query.ResourceQuery;
import com.xfwang.platform.auth.application.resource.view.ResourceTreeNode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Resource 管理入口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/platform/auth/resources")
public class AuthzResourceController {

    private final ResourceRegistry resourceRegistry;
    private final ResourceQueryService resourceQueryService;

    @SaCheckPermission("platform:auth:resource:query")
    @GetMapping("/page")
    public R<PageResult<ResourceDescriptor>> page(ResourceQuery query, PageQuery pageQuery) {
        return R.ok(resourceQueryService.page(query, pageQuery));
    }


    @SaCheckPermission("platform:auth:resource:query")
    @GetMapping("/{resourceId}")
    public R<ResourceDescriptor> get(@PathVariable Long resourceId) {
        return R.ok(resourceRegistry.findById(resourceId)
            .orElseThrow(() -> new ServiceException(GlobalErrorCode.NOT_FOUND, "授权资源不存在")));
    }

    @SaCheckPermission("platform:auth:resource:query")
    @GetMapping("/tree")
    public R<List<ResourceTreeNode>> tree(@RequestParam String resourceType,
                                          @RequestParam(required = false, defaultValue = "ACTIVE") String status) {
        return R.ok(resourceQueryService.tree(resourceType, status));
    }
}
