package com.carebridge.security;

import com.carebridge.common.error.ApiError;
import com.carebridge.common.error.ErrorCode;
import com.carebridge.identity.User;
import com.carebridge.identity.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Invited Users may authenticate, but must change their temporary password before calling
 * non-auth APIs. Allowed while {@code mustChangePassword}: {@code GET /api/v1/me} and {@code
 * POST /api/v1/auth/change-password}.
 */
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  public MustChangePasswordFilter(UserRepository userRepository, ObjectMapper objectMapper) {
    this.userRepository = userRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof AuthenticatedUser principal
        && !isAllowedWhileMustChangePassword(request)) {
      Optional<User> user = userRepository.findById(principal.userId());
      if (user.isPresent() && user.get().isMustChangePassword()) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getOutputStream(),
            ApiError.of(
                ErrorCode.MUST_CHANGE_PASSWORD,
                "Password change required before using this API",
                ""));
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  private static boolean isAllowedWhileMustChangePassword(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    if (HttpMethod.GET.matches(method) && path.equals("/api/v1/me")) {
      return true;
    }
    if (HttpMethod.POST.matches(method) && path.equals("/api/v1/auth/change-password")) {
      return true;
    }
    return false;
  }
}
