package com.example.demo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Entity
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Size(min = 7, max = 20, message = "사용자 이름은 7자 이상 20자 이하이어야 합니다.")
    private String username;
    @Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다.")
    private String password;
    @NotBlank(message = "권한은 필수입니다.")
    private String role;

}