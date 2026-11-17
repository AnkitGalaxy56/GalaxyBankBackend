package com.abcbank.portal.requests;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The signed-in portal user, taken from the verified JWT (never from the request body). */
public record Caller(long id, String name, String role, String branch) {

    private static final Set<String> PORTAL_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "STAFF", "USER");

    public boolean admin() { return role.equals("ADMIN") || role.equals("SUPER_ADMIN"); }

    /** Any portal user. Staff must have a branch. */
    public static Caller of(Authentication auth) {
        if (auth == null || auth.getName() == null || !(auth.getDetails() instanceof Map<?, ?> claims)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        long id;
        try { id = Long.parseLong(auth.getName()); }
        catch (NumberFormatException e) { throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session."); }
        String role = Objects.toString(claims.get("role"), "").toUpperCase(Locale.ROOT);
        if (!PORTAL_ROLES.contains(role)) throw new ApiException(HttpStatus.FORBIDDEN, "Your role cannot use this page.");
        String branch = Objects.toString(claims.get("branch"), "").trim();
        Caller c = new Caller(id, Objects.toString(claims.get("username"), ""), role, branch);
        if (!c.admin() && branch.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "Your user has no branch assigned.");
        return c;
    }

    /** Administrators only. */
    public static Caller admin(Authentication auth) {
        Caller c = of(auth);
        if (!c.admin()) throw new ApiException(HttpStatus.FORBIDDEN, "Only an administrator can do this.");
        return c;
    }
}
