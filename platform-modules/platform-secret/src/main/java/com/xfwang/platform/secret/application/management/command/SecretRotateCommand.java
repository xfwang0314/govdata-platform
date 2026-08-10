package com.xfwang.platform.secret.application.management.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 凭据版本轮换（Rotate）Command 请求对象。
 * <p>
 * 用于密码更新轮换，上传新版本的凭据明文 Map，并携带当前乐观锁版本号防止并发冲突。
 */
@Data
@ToString(exclude = "values")
public class SecretRotateCommand {

    /**
     * 新版本的凭据明文 Map
     */
    @NotEmpty
    private Map<String, @NotBlank String> values;

    /**
     * 轮换后的新过期时间（可选）
     */
    private LocalDateTime expireTime;

    /**
     * 凭据主表当前乐观锁版本号
     */
    private Integer lockVersion;
}
