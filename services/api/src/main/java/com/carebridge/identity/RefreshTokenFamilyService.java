package com.carebridge.identity;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Family revoke must commit even when the surrounding refresh request fails, so reuse detection
 * cannot be undone by rolling back the unauthorized response.
 */
@Service
public class RefreshTokenFamilyService {

  private final RefreshTokenRepository refreshTokenRepository;

  public RefreshTokenFamilyService(RefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revokeFamily(UUID familyId) {
    refreshTokenRepository.revokeFamily(familyId);
  }
}
