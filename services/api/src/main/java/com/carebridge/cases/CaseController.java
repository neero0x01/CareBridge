package com.carebridge.cases;

import com.carebridge.cases.dto.AssignCaseRequest;
import com.carebridge.cases.dto.CaseCommentResponse;
import com.carebridge.cases.dto.CaseResponse;
import com.carebridge.cases.dto.CaseTransitionResponse;
import com.carebridge.cases.dto.ClaimCaseRequest;
import com.carebridge.cases.dto.CreateCaseRequest;
import com.carebridge.cases.dto.CreateCommentRequest;
import com.carebridge.common.dto.PageResponse;
import com.carebridge.cases.dto.PatchCaseRequest;
import com.carebridge.cases.dto.TransitionCaseRequest;
import com.carebridge.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

  private final CaseService caseService;

  public CaseController(CaseService caseService) {
    this.caseService = caseService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'CLINICIAN')")
  public CaseResponse create(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CreateCaseRequest request) {
    return caseService.create(principal, request);
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public PageResponse<CaseResponse> list(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(required = false) CaseStatus status,
      @RequestParam(required = false) String assignee,
      @RequestParam(required = false) CasePriority priority,
      @RequestParam(required = false) String q,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return PageResponse.from(caseService.list(principal, status, assignee, priority, q, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public CaseResponse get(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
    return caseService.get(principal, id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public CaseResponse patch(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID id,
      @Valid @RequestBody PatchCaseRequest request) {
    return caseService.patch(principal, id, request);
  }

  @PostMapping("/{id}/claim")
  @PreAuthorize("isAuthenticated()")
  public CaseResponse claim(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID id,
      @Valid @RequestBody ClaimCaseRequest request) {
    return caseService.claim(principal, id, request);
  }

  @PostMapping("/{id}/assign")
  @PreAuthorize("isAuthenticated()")
  public CaseResponse assign(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID id,
      @Valid @RequestBody AssignCaseRequest request) {
    return caseService.assign(principal, id, request);
  }

  @PostMapping("/{id}/transitions")
  @PreAuthorize("isAuthenticated()")
  public CaseResponse transition(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID id,
      @Valid @RequestBody TransitionCaseRequest request) {
    return caseService.transition(principal, id, request);
  }

  @GetMapping("/{id}/transitions")
  @PreAuthorize("isAuthenticated()")
  public List<CaseTransitionResponse> listTransitions(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
    return caseService.listTransitions(principal, id);
  }

  @PostMapping("/{id}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  public CaseCommentResponse addComment(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID id,
      @Valid @RequestBody CreateCommentRequest request) {
    return caseService.addComment(principal, id, request);
  }

  @GetMapping("/{id}/comments")
  @PreAuthorize("isAuthenticated()")
  public List<CaseCommentResponse> listComments(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
    return caseService.listComments(principal, id);
  }
}
