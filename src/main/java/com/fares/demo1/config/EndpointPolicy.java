package com.fares.demo1.config;

/**
 * The two "speciality" flags an admin can set per metric key. {@code cached} controls
 * whether {@link EndpointPolicyRegistry} step 2 will serve that read from a short-lived
 * cache instead of hitting the store on every request; {@code protectedAccess} controls
 * whether step 3 will require the ADMIN role to read it instead of leaving it public.
 */
public record EndpointPolicy(boolean cached, boolean protectedAccess) {
}
