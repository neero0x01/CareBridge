package com.carebridge.cases;

import com.carebridge.cases.dto.CaseResponse;
import com.carebridge.cases.dto.CreateCaseRequest;
import com.carebridge.cases.dto.PatchCaseRequest;
import com.carebridge.common.error.ApiException;
import com.carebridge.common.error.ErrorCode;
import com.carebridge.identity.Role;
import com.carebridge.security.AuthenticatedUser;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseService {

  private final CaseRepository caseRepository;
  private final TenantCaseCounterRepository counterRepository;

  public CaseService(
      CaseRepository caseRepository, TenantCaseCounterRepository counterRepository) {
    this.caseRepository = caseRepository;
    this.counterRepository = counterRepository;
  }

  @Transactional
  public CaseResponse create(AuthenticatedUser principal, CreateCaseRequest request) {
    long seq = counterRepository.allocateNext(principal.tenantId());
    String caseNumber = "CB-" + seq;
    Instant now = Instant.now();

    CaseEntity entity =
        new CaseEntity(
            UUID.randomUUID(),
            principal.tenantId(),
            caseNumber,
            request.title().trim(),
            request.type(),
            request.priority(),
            CaseStatus.TO_DO,
            request.patientDisplayName().trim(),
            request.patientRef().trim(),
            request.description() != null ? request.description().trim() : null,
            principal.userId(),
            null,
            now,
            now);
    caseRepository.save(entity);
    return CaseResponse.from(entity);
  }

  @Transactional(readOnly = true)
  public CaseResponse get(AuthenticatedUser principal, UUID caseId) {
    return CaseResponse.from(requireCase(principal.tenantId(), caseId));
  }

  @Transactional(readOnly = true)
  public Page<CaseResponse> list(
      AuthenticatedUser principal,
      CaseStatus status,
      String assignee,
      CasePriority priority,
      String q,
      Pageable pageable) {
    Specification<CaseEntity> spec = CaseSpecifications.forTenant(principal.tenantId());

    if (status != null) {
      spec = spec.and(CaseSpecifications.withStatus(status));
    }
    if (priority != null) {
      spec = spec.and(CaseSpecifications.withPriority(priority));
    }
    if (assignee != null && !assignee.isBlank()) {
      if ("unassigned".equalsIgnoreCase(assignee) || "null".equalsIgnoreCase(assignee)) {
        spec = spec.and(CaseSpecifications.unassigned());
      } else {
        try {
          UUID assigneeId = UUID.fromString(assignee.trim());
          spec = spec.and(CaseSpecifications.withAssignee(assigneeId));
        } catch (IllegalArgumentException ex) {
          throw new ApiException(
              ErrorCode.VALIDATION_ERROR,
              HttpStatus.BAD_REQUEST,
              "assignee must be a UUID, 'unassigned', or 'null'");
        }
      }
    }
    if (q != null && !q.isBlank()) {
      spec = spec.and(CaseSpecifications.matchingQuery(q.trim()));
    }

    return caseRepository.findAll(spec, pageable).map(CaseResponse::from);
  }

  @Transactional
  public CaseResponse patch(AuthenticatedUser principal, UUID caseId, PatchCaseRequest request) {
    // Resolve tenant-scoped row first so cross-tenant access is always 404, never role 403.
    CaseEntity entity = requireCase(principal.tenantId(), caseId);

    if (principal.role() != Role.ORG_ADMIN && principal.role() != Role.CLINICIAN) {
      throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Forbidden");
    }

    if (entity.getVersion() != request.version()) {
      throw new ApiException(
          ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Case version conflict");
    }

    if (request.title() != null) {
      entity.setTitle(request.title().trim());
    }
    if (request.description() != null) {
      entity.setDescription(request.description().trim());
    }
    if (request.priority() != null) {
      entity.setPriority(request.priority());
    }
    entity.setUpdatedAt(Instant.now());

    try {
      caseRepository.flush();
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ApiException(
          ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Case version conflict");
    }
    return CaseResponse.from(entity);
  }

  private CaseEntity requireCase(UUID tenantId, UUID caseId) {
    return caseRepository
        .findByIdAndTenantId(caseId, tenantId)
        .orElseThrow(
            () -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Case not found"));
  }
}
