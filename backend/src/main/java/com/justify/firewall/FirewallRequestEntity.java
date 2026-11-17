package com.justify.firewall;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "firewall_request")
public class FirewallRequestEntity {

    @Id
    private UUID id;
    @Column(name = "ticket_no", nullable = false, unique = true)
    private String ticketNo;
    @Column(name = "requester_name", nullable = false)
    private String requesterName;
    @Column(name = "employee_id", nullable = false)
    private String employeeId;
    @Column(nullable = false)
    private String department;
    private String email;
    @Column(name = "source_hostname", nullable = false)
    private String sourceHostname;
    @Column(name = "source_ip", nullable = false)
    private String sourceIp;
    @Column(name = "destination_host", nullable = false)
    private String destinationHost;
    @Column(name = "destination_ip")
    private String destinationIp;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FirewallForm.Protocol protocol;
    @Column(nullable = false)
    private String ports;
    @Enumerated(EnumType.STRING)
    @Column(name = "duration_type", nullable = false)
    private FirewallForm.DurationType durationType;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Column(name = "submission_id", nullable = false, unique = true)
    private UUID submissionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;
    @Column(name = "decided_by")
    private String decidedBy;
    @Column(name = "decision_note")
    private String decisionNote;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected FirewallRequestEntity() {
    }

    public FirewallRequestEntity(UUID id, String ticketNo, FirewallForm f, String ports, UUID submissionId,
                                 RequestStatus status, Instant now) {
        this.id = id;
        this.ticketNo = ticketNo;
        this.requesterName = f.requesterName().strip();
        this.employeeId = f.employeeId().strip();
        this.department = f.department().strip();
        this.email = blankToNull(f.email());
        this.sourceHostname = f.sourceHostname().strip();
        this.sourceIp = f.sourceIp().strip();
        this.destinationHost = f.destinationHost().strip();
        this.destinationIp = blankToNull(f.destinationIp());
        this.protocol = f.protocol();
        this.ports = ports;
        this.durationType = f.durationType();
        this.endDate = f.durationType() == FirewallForm.DurationType.TEMPORARY ? f.endDate() : null;
        this.submissionId = submissionId;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public void moveTo(RequestStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }

    public void decide(RequestStatus decision, String engineer, String note, Instant now) {
        this.status = decision;
        this.decidedBy = engineer;
        this.decisionNote = note;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getTicketNo() { return ticketNo; }
    public String getRequesterName() { return requesterName; }
    public String getEmployeeId() { return employeeId; }
    public String getDepartment() { return department; }
    public String getEmail() { return email; }
    public String getSourceHostname() { return sourceHostname; }
    public String getSourceIp() { return sourceIp; }
    public String getDestinationHost() { return destinationHost; }
    public String getDestinationIp() { return destinationIp; }
    public FirewallForm.Protocol getProtocol() { return protocol; }
    public String getPorts() { return ports; }
    public FirewallForm.DurationType getDurationType() { return durationType; }
    public LocalDate getEndDate() { return endDate; }
    public UUID getSubmissionId() { return submissionId; }
    public RequestStatus getStatus() { return status; }
    public String getDecidedBy() { return decidedBy; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
