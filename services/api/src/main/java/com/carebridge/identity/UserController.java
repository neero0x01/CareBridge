package com.carebridge.identity;

import com.carebridge.identity.dto.InviteUserRequest;
import com.carebridge.identity.dto.PatchUserRequest;
import com.carebridge.identity.dto.UserResponse;
import com.carebridge.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ORG_ADMIN')")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public List<UserResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
    return userService.list(principal);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse invite(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody InviteUserRequest request) {
    return userService.invite(principal, request);
  }

  @PatchMapping("/{id}")
  public UserResponse patch(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID id,
      @Valid @RequestBody PatchUserRequest request) {
    return userService.patch(principal, id, request);
  }
}
