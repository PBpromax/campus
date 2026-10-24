
package com.example.campushelp.repository;

import com.example.campushelp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from User e where e.id=:id")
    java.util.Optional<User> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByStudentId(String studentId);
    Optional<User> findByPhone(String phone);
    boolean existsByStudentId(String studentId);
    boolean existsByPhone(String phone);
}
