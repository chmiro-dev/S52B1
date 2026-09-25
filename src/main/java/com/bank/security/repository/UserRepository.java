package com.bank.security.repository;

import com.bank.security.domain.UserEntity;
import java.util.Optional;

public interface UserRepository {
    Optional<UserEntity> findByUsername(String username);
}