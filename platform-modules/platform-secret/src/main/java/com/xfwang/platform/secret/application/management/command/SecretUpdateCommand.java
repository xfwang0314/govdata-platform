package com.xfwang.platform.secret.application.management.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SecretUpdateCommand {
    @NotBlank
    @Size(max = 100)
    private String secretName;

    @Size(max = 500)
    private String description;

    @NotBlank
    @Size(max = 500)
    private String purposeScope;

    private LocalDateTime expireTime;

    @NotNull
    private Integer lockVersion;
}
