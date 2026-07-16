package com.carebridge.identity.dto;

import com.carebridge.identity.Role;

/** Partial update: any field may be null (leave unchanged). */
public record PatchUserRequest(Role role, Boolean active) {}
