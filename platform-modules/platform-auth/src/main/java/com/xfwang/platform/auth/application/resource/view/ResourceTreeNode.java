package com.xfwang.platform.auth.application.resource.view;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 授权资源父子树节点。 */
@Data
@Builder
public class ResourceTreeNode {
    private Long id;
    private String label;
    private String code;
    private String status;
    private Long parentId;
    private List<ResourceTreeNode> children;
}
