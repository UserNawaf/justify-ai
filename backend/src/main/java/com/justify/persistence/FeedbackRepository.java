package com.justify.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, UUID> {

    long countBySufficientTrue();
}
