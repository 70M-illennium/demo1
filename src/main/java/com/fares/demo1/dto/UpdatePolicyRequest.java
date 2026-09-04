package com.fares.demo1.dto;

/**
 * Body for {@code PATCH /api/admin/policies/{key}}. Both fields optional (null = leave
 * that flag unchanged), same partial-update shape as {@code UpdateThresholdsRequest}.
 */
public record UpdatePolicyRequest(Boolean cached, Boolean protectedAccess) {
}
