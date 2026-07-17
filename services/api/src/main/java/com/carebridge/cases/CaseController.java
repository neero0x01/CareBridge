package com.carebridge.cases;

import com.carebridge.cases.dto.CaseResponse;
import com.carebridge.cases.dto.CreateCaseRequest;
import com.carebridge.cases.dto.PageResponse;
import com.carebridge.cases.dto.PatchCaseRequest;
import com.carebridge.security.AuthenticatedUser;
import jakarta.validation.Valid;
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
}
