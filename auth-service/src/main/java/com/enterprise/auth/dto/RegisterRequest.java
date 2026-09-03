package com.enterprise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder\n@NoArgsConstructor\n@AllArgsConstructor\npublic class RegisterRequest {\n    private String username;\n    private String email;\n    private String password;\n    private String role;\n}
