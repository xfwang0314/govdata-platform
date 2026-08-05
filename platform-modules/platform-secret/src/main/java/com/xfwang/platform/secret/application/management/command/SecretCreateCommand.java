package com.xfwang.platform.secret.application.management.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@ToString(exclude = "values")
public class SecretCreateCommand {
    @NotBlank @Size(max = 100) private String secretName;
    @Size(max = 500) private String description;
    @NotBlank private String secretType;
    @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") private String ownerModule;
    @NotBlank @Size(max = 500) private String purposeScope;
    @NotEmpty private Map<@Pattern(regexp = "^[a-z][a-zA-Z0-9_]{0,63}$") String, @NotBlank String> values;
    private LocalDateTime expireTime;
}
