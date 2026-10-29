package com.xfwang.platform.auth.application.resource.view;

import lombok.Builder;
import lombok.Data;

/** 授权管理端按真实 parentId 懒加载的资源目录节点。 */
@Data
@Builder
public class ResourceDirectoryNode {
    private Long id;
    private Long parentId;
    private String label;
    private String code;
    private String resourceType;
    private String sourceModule;
    private String status;
    private boolean leaf;
}
