package com.abcbank.portal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import com.abcbank.portal.requests.ApiException;
import com.abcbank.portal.requests.AuditLog;
import com.abcbank.portal.requests.Caller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Bank themes. GET endpoints are public (see SecurityConfig) because the login page
 * needs the theme before anyone is signed in. Write endpoints require an admin role.
 * Themes are stored as JSON in public.portal_bank_theme (created automatically).
 */
@RestController
public class ThemeController {

    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final AuditLog audit;

    public ThemeController(JdbcTemplate db, ObjectMapper mapper, AuditLog audit) {
        this.db = db;
        this.mapper = mapper;
        this.audit = audit;
    }

    @PostConstruct
    void init() {
        db.execute("create table if not exists public.portal_bank_theme (" +
                "code varchar(100) primary key, theme_json text not null, updated_at timestamptz default now())");
        db.execute("alter table public.portal_bank_theme add column if not exists bank_id varchar(100)");
        // Link existing bank theme rows to MB002001 using the bank code (the bank master identifier).
        db.update("update public.portal_bank_theme t set bank_id=b.bank_code from public.\"MB002001\" b where t.bank_id is null and lower(t.code)=lower(b.bank_code)");
        seed("allianza", defaultTheme("allianza", "Allianza", "Allianza", "#0F766E", "#0B4F4A", "#E6F4F2"));
    }

    private void seed(String code, Map<String, Object> theme) {
        try {
            db.update("insert into public.portal_bank_theme(code, theme_json) values (?,?) on conflict (code) do nothing",
                    code, mapper.writeValueAsString(theme));
        } catch (Exception e) {
            throw new IllegalStateException("Could not seed theme " + code, e);
        }
    }

    private Map<String, Object> defaultTheme(String code, String name, String shortName,
                                             String primary, String primaryDark, String tint) {
        Map<String, Object> colors = new LinkedHashMap<>();
        colors.put("primary", primary);
        colors.put("primaryDark", primaryDark);
        colors.put("primaryTint", tint);
        colors.put("background", "#F5F7FA");
        colors.put("surface", "#FFFFFF");
        colors.put("textPrimary", "#1A2433");
        colors.put("textSecondary", "#5B6778");
        colors.put("border", "#E1E6ED");
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("code", code);
        t.put("name", name);
        t.put("shortName", shortName);
        t.put("tagline", "Secure digital banking");
        t.put("fontFamily", "Inter, Roboto, sans-serif");
        t.put("borderRadius", 8);
        t.put("colors", colors);
        return t;
    }

    private JsonNode read(String json) {
        try { return mapper.readTree(json); }
        catch (Exception e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored theme is invalid"); }
    }

    private JsonNode find(String code) {
        List<String> rows = db.query("select theme_json from public.portal_bank_theme where code=?",
                (rs, n) -> rs.getString(1), code);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Theme not found: " + code);
        return read(rows.get(0));
    }

    // PUBLIC
    @GetMapping("/banks/{code}/theme")
    public JsonNode bankTheme(@PathVariable String code) { return find(code); }

    @GetMapping("/themes/{code}")
    public JsonNode byCode(@PathVariable String code) { return find(code); }

    @GetMapping("/themes")
    public List<JsonNode> list() {
        return db.query("select theme_json from public.portal_bank_theme order by code",
                (rs, n) -> read(rs.getString(1)));
    }

    // ADMIN ONLY
    @PostMapping("/themes")
    public JsonNode create(@RequestBody JsonNode theme, Authentication auth, HttpServletRequest http) { return save(theme.path("code").asText(""), theme, true, auth, http); }

    @PutMapping("/themes/{code}")
    public JsonNode update(@PathVariable String code, @RequestBody JsonNode theme, Authentication auth, HttpServletRequest http) { return save(code, theme, false, auth, http); }

    @DeleteMapping("/themes/{code}")
    public Map<String, String> delete(@PathVariable String code, Authentication auth, HttpServletRequest http) {
        if ("allianza".equalsIgnoreCase(code))
            throw new ApiException(HttpStatus.BAD_REQUEST, "The platform theme cannot be deleted");
        db.update("delete from public.portal_bank_theme where code=?", code);
        audit.record(Caller.of(auth), http, "DELETE", "theme", null, "code=" + code);
        return Map.of("message", "Deleted");
    }

    private JsonNode save(String code, JsonNode theme, boolean isNew, Authentication auth, HttpServletRequest http) {
        if (code.isBlank() || !code.matches("[A-Za-z0-9._-]{1,100}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid theme code");
        if (!theme.has("colors") || !theme.has("name"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Theme name and colors are required");
        validate(theme);
        try {
            String json = mapper.writeValueAsString(((com.fasterxml.jackson.databind.node.ObjectNode) theme.deepCopy()).put("code", code));
            db.update("insert into public.portal_bank_theme(code, theme_json, bank_id) values (?,?, " +
                    "(select bank_code from public.\"MB002001\" where lower(bank_code)=lower(?))) " +
                    "on conflict (code) do update set theme_json=excluded.theme_json, bank_id=coalesce(excluded.bank_id, public.portal_bank_theme.bank_id), updated_at=now()", code, json, code);
            audit.record(Caller.of(auth), http, isNew ? "CREATE" : "UPDATE", "theme", null, "code=" + code);
            return read(json);
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid theme payload"); }
    }

    private static final java.util.regex.Pattern HEX = java.util.regex.Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final java.util.regex.Pattern LOGO = java.util.regex.Pattern.compile("^(https://[^\\s<>\"']+|data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/]+={0,2})$");

    /** Theme values are injected into every page, so they are checked strictly. */
    private void validate(JsonNode t) {
        if (t.toString().length() > 500_000) bad("The theme is too large.");
        text(t, "name", 100, true);
        text(t, "shortName", 50, false);
        text(t, "tagline", 150, false);
        String font = t.path("fontFamily").asText("");
        if (font.length() > 200 || font.matches(".*[;{}<>\\\\].*")) bad("Font family contains invalid characters.");
        if (t.hasNonNull("borderRadius") && (!t.get("borderRadius").isNumber() || t.get("borderRadius").asInt() < 0 || t.get("borderRadius").asInt() > 32))
            bad("Border radius must be between 0 and 32.");
        String logo = t.path("logoUrl").asText("");
        if (!logo.isEmpty() && (logo.length() > 300_000 || !LOGO.matcher(logo).matches()))
            bad("Logo must be an https:// link or a PNG, JPEG, WebP or GIF image (up to about 200 KB).");
        palette(t.path("colors"), "colors", true);
        if (t.hasNonNull("mobileColors")) palette(t.get("mobileColors"), "mobileColors", false);
        String css = t.path("customCss").asText("");
        String lower = css.toLowerCase(java.util.Locale.ROOT);
        if (css.length() > 20_000 || lower.contains("</") || lower.contains("@import") || lower.contains("javascript:") || lower.contains("expression(")
                || lower.contains("<!--") || lower.contains("behavior:") || lower.contains("-moz-binding"))
            bad("Custom CSS is too long or contains blocked content.");
    }

    private void text(JsonNode t, String key, int max, boolean required) {
        String v = t.path(key).asText("").trim();
        if (required && v.isEmpty()) bad("Theme " + key + " is required.");
        if (v.length() > max || v.chars().anyMatch(c -> c < 0x20) || v.indexOf('<') >= 0 || v.indexOf('>') >= 0) bad("Theme " + key + " is too long or has invalid characters.");
    }

    private void palette(JsonNode c, String label, boolean required) {
        for (String k : List.of("primary", "primaryDark", "primaryTint", "background", "surface", "textPrimary", "textSecondary", "border")) {
            if (!c.hasNonNull(k)) { if (required) bad("Colour " + k + " is required."); continue; }
            if (!HEX.matcher(c.get(k).asText()).matches()) bad("Colour " + k + " must look like #1A2B3C.");
        }
        for (String k : List.of("secondary", "authPanel")) {
            if (c.hasNonNull(k) && !HEX.matcher(c.get(k).asText()).matches()) bad("Colour " + k + " must look like #1A2B3C.");
        }
    }

    private static void bad(String message) {
        throw new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
