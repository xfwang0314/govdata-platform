package com.xfwang.platform.mcp.application.token.view;

import lombok.Builder;
import lombok.Data;

/** 创建或轮换成功响应；明文凭据只在本次响应中出现。 */
@Data
@Builder
public class AgentTokenIssuedView {
    private AgentTokenView token;
    private String plainToken;
    private String streamableHttpUrl;
    private String clientConfigJson;
}
