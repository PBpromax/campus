
package com.example.campushelp.repository;

import com.example.campushelp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByStudentId(String studentId);
    Optional<User> findByPhone(String phone);
    boolean existsByStudentId(String studentId);
    boolean existsByPhone(String phone);
}
