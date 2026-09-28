package com.bank.security.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

import com.bank.security.domain.UserEntity;
import java.util.Optional;

public class UserRepository {

    private final EntityManager entityManager;

    public UserRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /*
     * public UserEntity save(UserEntity user) {
     * if (user.getId() == null) {
     * entityManager.persist(user);
     * return user;
     * } else {
     * return entityManager.merge(user);
     * }
     * }
     */

    public Optional<UserEntity> findById(Long id) {
        return Optional.ofNullable(entityManager.find(UserEntity.class, id));
    }

    /**
     * Finds a User entity by username using a parameter-bound JPQL query.
     * 
     * @param username The unique username to search for.
     * @return An Optional containing the User if found, or Optional.empty() if not
     *         found.
     */
    public Optional<UserEntity> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        TypedQuery<UserEntity> query = entityManager.createQuery(
                "SELECT u FROM UserEntity u WHERE u.username = :username",
                UserEntity.class);
        query.setParameter("username", username);

        try {
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public void deleteAll() {
        entityManager.getTransaction().begin();
        entityManager.createQuery("DELETE FROM User u").executeUpdate();
        entityManager.getTransaction().commit();
    }
}
