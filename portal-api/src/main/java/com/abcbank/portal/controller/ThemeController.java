package com.abcbank.portal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

    public ThemeController(JdbcTemplate db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    @PostConstruct
    void init() {
        db.execute("create table if not exists public.portal_bank_theme (" +
                "code varchar(100) primary key, theme_json text not null, updated_at timestamptz default now())");
        seed("sample-urban", defaultTheme("sample-urban", "Sample Urban Co-op Bank", "Sample Urban", "#1F5FBF", "#173F7A", "#E8F0FC"));
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
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored theme is invalid"); }
    }

    private JsonNode find(String code) {
        List<String> rows = db.query("select theme_json from public.portal_bank_theme where code=?",
                (rs, n) -> rs.getString(1), code);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Theme not found: " + code);
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
    public JsonNode create(@RequestBody JsonNode theme) { return save(theme.path("code").asText(""), theme, true); }

    @PutMapping("/themes/{code}")
    public JsonNode update(@PathVariable String code, @RequestBody JsonNode theme) { return save(code, theme, false); }

    @DeleteMapping("/themes/{code}")
    public Map<String, String> delete(@PathVariable String code) {
        if ("allianza".equalsIgnoreCase(code))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The platform theme cannot be deleted");
        db.update("delete from public.portal_bank_theme where code=?", code);
        return Map.of("message", "Deleted");
    }

    private JsonNode save(String code, JsonNode theme, boolean isNew) {
        if (code.isBlank() || !code.matches("[A-Za-z0-9._-]{1,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid theme code");
        if (!theme.has("colors") || !theme.has("name"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Theme name and colors are required");
        try {
            String json = mapper.writeValueAsString(((com.fasterxml.jackson.databind.node.ObjectNode) theme.deepCopy()).put("code", code));
            db.update("insert into public.portal_bank_theme(code, theme_json) values (?,?) " +
                    "on conflict (code) do update set theme_json=excluded.theme_json, updated_at=now()", code, json);
            return read(json);
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid theme payload"); }
    }
}
