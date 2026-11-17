package com.justify.firewall;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FirewallRequestRepository extends JpaRepository<FirewallRequestEntity, UUID> {

    Optional<FirewallRequestEntity> findBySubmissionId(UUID submissionId);

    Optional<FirewallRequestEntity> findByTicketNo(String ticketNo);

    @Query(value = "select nextval('firewall_ticket_seq')", nativeQuery = true)
    long nextTicketNumber();

    /** Optional filters; {@code q} matches ticket, requester, employee id, hosts and IPs. */
    @Query(value = """
            select f, s.qualityScore from FirewallRequestEntity f, SubmissionEntity s
            where s.id = f.submissionId
              and (:status is null or f.status = :status)
              and (:employeeId is null or f.employeeId = :employeeId)
              and (:q is null
                   or lower(f.ticketNo) like :q or lower(f.requesterName) like :q or lower(f.employeeId) like :q
                   or lower(f.sourceHostname) like :q or lower(f.sourceIp) like :q
                   or lower(f.destinationHost) like :q or lower(coalesce(f.destinationIp, '')) like :q)
            order by f.createdAt desc
            """,
            countQuery = """
            select count(f) from FirewallRequestEntity f
            where (:status is null or f.status = :status)
              and (:employeeId is null or f.employeeId = :employeeId)
              and (:q is null
                   or lower(f.ticketNo) like :q or lower(f.requesterName) like :q or lower(f.employeeId) like :q
                   or lower(f.sourceHostname) like :q or lower(f.sourceIp) like :q
                   or lower(f.destinationHost) like :q or lower(coalesce(f.destinationIp, '')) like :q)
            """)
    Page<Object[]> search(@Param("status") RequestStatus status, @Param("employeeId") String employeeId,
                                       @Param("q") String q, Pageable pageable);
}
