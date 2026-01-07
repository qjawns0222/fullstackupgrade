package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository; // <-- 이거 필수
import com.example.demo.entity.User; // <-- 엔티티 위치 알려주기

public interface PostRepository extends JpaRepository<User, Long>, PostRepositoryCustom {

}
