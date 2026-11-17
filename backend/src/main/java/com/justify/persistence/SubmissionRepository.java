package com.justify.persistence;

import com.justify.core.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<SubmissionEntity, UUID> {

    Optional<SubmissionEntity> findBySourceAndExternalRef(String source, String externalRef);

    long countByStatus(Status status);

    @Query("select avg(s.qualityScore) from SubmissionEntity s where s.status <> com.justify.core.Status.NEEDS_INFO")
    Double averageQualityOfClosed();

    @Query("select avg(s.roundCount) from SubmissionEntity s where s.status <> com.justify.core.Status.NEEDS_INFO")
    Double averageRoundsOfClosed();

    @Query("select count(s) from SubmissionEntity s where s.status = com.justify.core.Status.COMPLETE and s.roundCount = 1")
    long countFirstPassComplete();
}
