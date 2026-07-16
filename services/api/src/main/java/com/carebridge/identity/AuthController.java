package com.carebridge.identity;

import com.carebridge.identity.dto.LoginRequest;
import com.carebridge.identity.dto.RegisterTenantRequest;
import com.carebridge.identity.dto.RegisterTenantResponse;
import com.carebridge.identity.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register-tenant")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterTenantResponse registerTenant(@Valid @RequestBody RegisterTenantRequest request) {
    return authService.registerTenant(request);
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }
}
