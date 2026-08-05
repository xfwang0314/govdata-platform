package com.xfwang.platform.secret.application.management.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@ToString(exclude = "values")
public class SecretRotateCommand {
    @NotEmpty private Map<String, @NotBlank String> values;
    private LocalDateTime expireTime;
    private Integer lockVersion;
}
