package com.carebridge.cases;

import com.carebridge.cases.dto.AssignCaseRequest;
import com.carebridge.cases.dto.CaseResponse;
import com.carebridge.cases.dto.CaseTransitionResponse;
import com.carebridge.cases.dto.ClaimCaseRequest;
import com.carebridge.cases.dto.CreateCaseRequest;
import com.carebridge.cases.dto.PatchCaseRequest;
import com.carebridge.cases.dto.TransitionCaseRequest;
import com.carebridge.common.error.ApiException;
import com.carebridge.common.error.ErrorCode;
import com.carebridge.identity.Role;
import com.carebridge.identity.User;
import com.carebridge.identity.UserRepository;
import com.carebridge.security.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
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
  private final CaseTransitionRepository transitionRepository;
  private final UserRepository userRepository;

  public CaseService(
      CaseRepository caseRepository,
      TenantCaseCounterRepository counterRepository,
      CaseTransitionRepository transitionRepository,
      UserRepository userRepository) {
    this.caseRepository = caseRepository;
    this.counterRepository = counterRepository;
    this.transitionRepository = transitionRepository;
    this.userRepository = userRepository;
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
    CaseEntity entity = requireCase(principal.tenantId(), caseId);
    assertVersion(entity, request.version());

    if (!CaseAuthz.canEditFields(
        principal.role(), entity.getStatus(), principal.userId(), entity.getCreatedBy())) {
      throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Forbidden");
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
    flushOptimistic(entity);
    return CaseResponse.from(entity);
  }

  @Transactional
  public CaseResponse claim(AuthenticatedUser principal, UUID caseId, ClaimCaseRequest request) {
    CaseEntity entity = requireCase(principal.tenantId(), caseId);
    assertVersion(entity, request.version());

    if (!CaseAuthz.canClaim(principal.role(), entity.getStatus(), entity.getAssigneeId())) {
      throw new ApiException(
          ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Cannot claim this Case");
    }

    CaseStatus from = entity.getStatus();
    entity.setAssigneeId(principal.userId());
    entity.setStatus(CaseStatus.IN_REVIEW);
    entity.setUpdatedAt(Instant.now());
    recordTransition(entity, from, CaseStatus.IN_REVIEW, principal.userId(), "Claimed");
    flushOptimistic(entity);
    return CaseResponse.from(entity);
  }

  @Transactional
  public CaseResponse assign(AuthenticatedUser principal, UUID caseId, AssignCaseRequest request) {
    CaseEntity entity = requireCase(principal.tenantId(), caseId);
    assertVersion(entity, request.version());

    if (principal.role() != Role.ORG_ADMIN) {
      throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Forbidden");
    }
    if (CaseWorkflow.isTerminal(entity.getStatus())) {
      throw new ApiException(
          ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Cannot assign a terminal Case");
    }

    User assignee =
        userRepository
            .findByIdAndTenantId(request.assigneeId(), principal.tenantId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.INVALID_ASSIGNEE,
                        HttpStatus.BAD_REQUEST,
                        "Assignee not found in tenant"));

    if (!assignee.isActive() || !CaseAuthz.canAssign(principal.role(), assignee.getRole())) {
      throw new ApiException(
          ErrorCode.INVALID_ASSIGNEE,
          HttpStatus.BAD_REQUEST,
          "Assignee must be an active REVIEWER in this Tenant");
    }

    CaseStatus from = entity.getStatus();
    entity.setAssigneeId(assignee.getId());
    Instant now = Instant.now();
    entity.setUpdatedAt(now);

    if (from == CaseStatus.TO_DO) {
      entity.setStatus(CaseStatus.IN_REVIEW);
      recordTransition(
          entity, from, CaseStatus.IN_REVIEW, principal.userId(), "Assigned to reviewer");
    }

    flushOptimistic(entity);
    return CaseResponse.from(entity);
  }

  @Transactional
  public CaseResponse transition(
      AuthenticatedUser principal, UUID caseId, TransitionCaseRequest request) {
    CaseEntity entity = requireCase(principal.tenantId(), caseId);
    assertVersion(entity, request.version());

    CaseStatus from = entity.getStatus();
    CaseStatus to = request.toStatus();

    if (!CaseWorkflow.isLegalEdge(from, to)) {
      throw illegalTransition(from, to);
    }
    if (!CaseAuthz.canTransition(
        principal.role(),
        from,
        to,
        principal.userId(),
        entity.getCreatedBy(),
        entity.getAssigneeId())) {
      throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Forbidden");
    }

    // NEEDS_INFO keeps assignee unchanged (do not clear)
    entity.setStatus(to);
    entity.setUpdatedAt(Instant.now());
    String comment = request.comment() != null ? request.comment().trim() : null;
    recordTransition(entity, from, to, principal.userId(), comment);
    flushOptimistic(entity);
    return CaseResponse.from(entity);
  }

  @Transactional(readOnly = true)
  public List<CaseTransitionResponse> listTransitions(AuthenticatedUser principal, UUID caseId) {
    requireCase(principal.tenantId(), caseId);
    return transitionRepository
        .findByCaseIdAndTenantIdOrderByCreatedAtAsc(caseId, principal.tenantId())
        .stream()
        .map(CaseTransitionResponse::from)
        .toList();
  }

  private void recordTransition(
      CaseEntity entity, CaseStatus from, CaseStatus to, UUID actorId, String comment) {
    transitionRepository.save(
        new CaseTransitionEntity(
            UUID.randomUUID(),
            entity.getId(),
            entity.getTenantId(),
            from,
            to,
            actorId,
            comment,
            Instant.now()));
  }

  private void assertVersion(CaseEntity entity, long expectedVersion) {
    if (entity.getVersion() != expectedVersion) {
      throw new ApiException(
          ErrorCode.VERSION_CONFLICT, HttpStatus.CONFLICT, "Case version conflict");
    }
  }

  private void flushOptimistic(CaseEntity entity) {
    try {
      caseRepository.flush();
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ApiException(
          ErrorCode.VERSION_CONFLICT, HttpStatus.CONFLICT, "Case version conflict");
    }
  }

  private static ApiException illegalTransition(CaseStatus from, CaseStatus to) {
    return new ApiException(
        ErrorCode.ILLEGAL_TRANSITION,
        HttpStatus.CONFLICT,
        "Cannot move from " + from + " to " + to);
  }

  private CaseEntity requireCase(UUID tenantId, UUID caseId) {
    return caseRepository
        .findByIdAndTenantId(caseId, tenantId)
        .orElseThrow(
            () -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Case not found"));
  }
}
