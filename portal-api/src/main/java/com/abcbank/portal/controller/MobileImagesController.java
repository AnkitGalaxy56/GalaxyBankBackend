package com.abcbank.portal.controller;

import com.abcbank.portal.requests.ApiException;
import com.abcbank.portal.requests.AuditLog;
import com.abcbank.portal.requests.Caller;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Mobile application images (table MB006001).
 * Staff can list and view; only administrators can add or edit.
 * Uploaded files are checked by their real content (PNG, JPEG, GIF, WebP only), never by file name.
 */
@RestController
@RequestMapping("/mobile-utilities/images")
public class MobileImagesController {

    private static final Logger log = LoggerFactory.getLogger(MobileImagesController.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("Banner", "App Logo", "Icon", "Onboarding", "Promotion", "Other");
    private static final Set<String> PLATFORMS = Set.of("Android", "iOS", "Both");
    private static final Set<String> STATUSES = Set.of("Active", "Inactive");
    private static final Pattern PATH = Pattern.compile("^(https://[^\\s<>\"']+|/[A-Za-z0-9._~!$&()*+,;=:@%/\\- ]+)$");
    private static final Pattern DIMENSIONS = Pattern.compile("^[0-9]{1,5} ?[×xX] ?[0-9]{1,5}$");
    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9]{1,10}$");
    private static final Pattern THUMB = Pattern.compile("^data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/]+={0,2}$");
    private static final int MAX_THUMB = 80_000;
    private static final String COLUMNS = "id, image_name, image_type, image_path, file_size, file_format, dimensions, alt_text, platform, status, "
            + "thumb_data, (file_data is not null) as has_file, created_on, modified_on";

    private final JdbcTemplate db;
    private final AuditLog audit;
    private volatile Boolean tableReady;

    public MobileImagesController(JdbcTemplate db, AuditLog audit) { this.db = db; this.audit = audit; }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int pageSize,
                                    @RequestParam(required = false) String search, @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String type, Authentication auth) {
        Caller.of(auth);
        ready();
        int size = Math.min(Math.max(pageSize, 1), 200), pg = Math.max(page, 1);
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null && STATUSES.contains(status)) { where.append(" and status = ?"); args.add(status); }
        if (type != null && TYPES.contains(type)) { where.append(" and image_type = ?"); args.add(type); }
        if (search != null && !search.isBlank()) {
            String s = search.trim();
            if (s.length() > 60) throw new ApiException(HttpStatus.BAD_REQUEST, "Search text is too long.");
            String like = "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" and (image_name ilike ? or image_path ilike ? or alt_text ilike ? or file_format ilike ?)");
            for (int i = 0; i < 4; i++) args.add(like);
        }
        args.add(size);
        args.add((long) (pg - 1) * size);
        List<Map<String, Object>> rows = db.queryForList("select " + COLUMNS + ", count(*) over() as _total from public.\"MB006001\""
                + where + " order by coalesce(modified_on, created_on) desc, id desc limit ? offset ?", args.toArray());
        long total = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) { total = ((Number) r.get("_total")).longValue(); items.add(record(r)); }
        return Map.of("items", items, "total", total, "page", pg, "pageSize", size);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id, Authentication auth) {
        Caller.of(auth);
        ready();
        List<Map<String, Object>> rows = db.queryForList("select " + COLUMNS + " from public.\"MB006001\" where id = ?", id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Image not found.");
        return record(rows.get(0));
    }

    /** The stored image file (for download or for the mobile app). */
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable long id, Authentication auth) {
        Caller.of(auth);
        ready();
        List<Map<String, Object>> rows = db.queryForList("select file_data, file_mime from public.\"MB006001\" where id = ? and file_data is not null", id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "No file is stored for this image.");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(rows.get(0).get("file_mime").toString()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body((byte[]) rows.get(0).get("file_data"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Object>> create(@RequestParam Map<String, String> form,
                                                      @RequestParam(value = "file", required = false) MultipartFile file,
                                                      Authentication auth, HttpServletRequest http) {
        Caller who = Caller.admin(auth);
        ready();
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> v = new LinkedHashMap<>();
        fields(form, true, errors, v);
        applyFile(file, v, errors);
        applyThumb(form, v, errors);
        if (!v.containsKey("file_data") && !v.containsKey("file_size")) v.put("file_size", size(form.get("size")));
        fail(errors);
        v.put("created_by", who.id());
        try {
            List<String> cols = new ArrayList<>(v.keySet());
            List<Map<String, Object>> rows = db.queryForList("insert into public.\"MB006001\" (" + quoted(cols) + ") values ("
                    + String.join(",", Collections.nCopies(cols.size(), "?")) + ") returning " + COLUMNS, cols.stream().map(v::get).toArray());
            Map<String, Object> out = record(rows.get(0));
            audit.record(who, http, "CREATE", "mobile-image", Long.parseLong(out.get("id").toString()), "name=" + v.get("image_name"));
            return ResponseEntity.status(HttpStatus.CREATED).body(out);
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "An image with this name already exists.", Map.of("name", "An image with this name already exists."));
        } catch (DataAccessException e) {
            log.warn("Image insert rejected: {}", e.getMostSpecificCause().getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "The image details were rejected by the database rules.");
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String, Object> update(@PathVariable long id, @RequestParam Map<String, String> form,
                                      @RequestParam(value = "file", required = false) MultipartFile file,
                                      Authentication auth, HttpServletRequest http) {
        Caller who = Caller.admin(auth);
        ready();
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> v = new LinkedHashMap<>();
        fields(form, false, errors, v);
        applyFile(file, v, errors);
        applyThumb(form, v, errors);
        fail(errors);
        if (v.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing to update.");
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (var e : v.entrySet()) { sets.add("\"" + e.getKey() + "\" = ?"); args.add(e.getValue()); }
        sets.add("\"modified_by\" = ?");
        args.add(who.id());
        sets.add("\"modified_on\" = now()");
        args.add(id);
        try {
            List<Map<String, Object>> rows = db.queryForList("update public.\"MB006001\" set " + String.join(", ", sets)
                    + " where id = ? returning " + COLUMNS, args.toArray());
            if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Image not found.");
            audit.record(who, http, "UPDATE", "mobile-image", id, "fields=" + String.join(",", v.keySet().stream().filter(k -> !k.equals("file_data") && !k.equals("thumb_data")).toList()));
            return record(rows.get(0));
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "An image with this name already exists.", Map.of("name", "An image with this name already exists."));
        }
    }

    // ------------------------------------------------------------------ validation

    private void fields(Map<String, String> f, boolean create, Map<String, String> errors, Map<String, Object> v) {
        text(f, "name", "image_name", 100, create, errors, v);
        choice(f, "type", "image_type", TYPES, create, errors, v);
        String path = f.get("path");
        if (path != null || create) {
            String p = path == null ? "" : path.trim();
            if (p.isEmpty()) errors.put("path", "Image path is required. Upload an image or enter its path.");
            else if (p.length() > 2048 || !PATH.matcher(p).matches() || p.contains("..")) errors.put("path", "Enter a valid image path (starting with / or https://).");
            else v.put("image_path", p);
        }
        optional(f, "format", "file_format", FORMAT, "File format may contain letters and digits only.", errors, v);
        optional(f, "dimensions", "dimensions", DIMENSIONS, "Dimensions must look like 1200 × 480.", errors, v);
        if (f.get("altText") != null) {
            String a = f.get("altText").trim();
            if (a.length() > 255 || a.indexOf('<') >= 0 || a.indexOf('>') >= 0) errors.put("altText", "Description is too long or has invalid characters.");
            else v.put("alt_text", a.isEmpty() ? null : a);
        }
        choice(f, "platform", "platform", PLATFORMS, create, errors, v);
        choice(f, "status", "status", STATUSES, create, errors, v);
    }

    private void text(Map<String, String> f, String key, String col, int max, boolean required, Map<String, String> errors, Map<String, Object> v) {
        String s = f.get(key);
        if (s == null) { if (required) errors.put(key, "Image name is required."); return; }
        s = s.trim();
        if (s.isEmpty()) { errors.put(key, "Image name is required."); return; }
        if (s.length() > max || s.chars().anyMatch(c -> c < 0x20) || s.indexOf('<') >= 0 || s.indexOf('>') >= 0) {
            errors.put(key, "Image name is too long or has invalid characters."); return;
        }
        v.put(col, s);
    }

    private void choice(Map<String, String> f, String key, String col, Set<String> allowed, boolean required, Map<String, String> errors, Map<String, Object> v) {
        String s = f.get(key);
        if (s == null) { if (required) errors.put(key, "Select a value."); return; }
        s = s.trim();
        if (!allowed.contains(s)) errors.put(key, "Choose one of: " + String.join(", ", new TreeSet<>(allowed)) + ".");
        else v.put(col, s);
    }

    private void optional(Map<String, String> f, String key, String col, Pattern p, String msg, Map<String, String> errors, Map<String, Object> v) {
        String s = f.get(key);
        if (s == null) return;
        s = s.trim();
        if (s.isEmpty()) v.put(col, null);
        else if (!p.matcher(s).matches()) errors.put(key, msg);
        else v.put(col, s);
    }

    private static long size(String s) {
        try { return Math.max(0, Long.parseLong(s == null ? "0" : s.trim())); } catch (NumberFormatException e) { return 0; }
    }

    private void applyFile(MultipartFile file, Map<String, Object> v, Map<String, String> errors) {
        if (file == null || file.isEmpty()) return;
        if (file.getSize() > MAX_BYTES) { errors.put("path", "Image must be 10 MB or smaller."); return; }
        try {
            byte[] bytes = file.getBytes();
            String[] kind = sniff(bytes);
            if (kind == null) { errors.put("path", "Only PNG, JPEG, GIF or WebP images are allowed."); return; }
            v.put("file_data", bytes);
            v.put("file_mime", kind[0]);
            v.put("file_format", kind[1]);
            v.put("file_size", (long) bytes.length);
        } catch (IOException e) {
            errors.put("path", "The file could not be read.");
        }
    }

    /** Real file type from the first bytes: {mime, format}. */
    private static String[] sniff(byte[] b) {
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return new String[]{"image/png", "PNG"};
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return new String[]{"image/jpeg", "JPG"};
        if (b.length > 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return new String[]{"image/gif", "GIF"};
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return new String[]{"image/webp", "WEBP"};
        return null;
    }

    private void applyThumb(Map<String, String> f, Map<String, Object> v, Map<String, String> errors) {
        String t = f.get("thumbnail");
        if (t == null || t.isBlank()) return;
        if (t.length() > MAX_THUMB || !THUMB.matcher(t).matches()) { errors.put("path", "The image preview is not valid."); return; }
        v.put("thumb_data", t);
    }

    private void fail(Map<String, String> errors) {
        if (errors.isEmpty()) return;
        throw new ApiException(HttpStatus.BAD_REQUEST, String.join(" ", errors.values().stream().limit(3).toList()), errors);
    }

    private static String quoted(Collection<String> names) {
        return String.join(", ", names.stream().map(n -> "\"" + n + "\"").toList());
    }

    private void ready() {
        if (Boolean.TRUE.equals(tableReady)) return;
        Boolean ok = db.queryForObject("select to_regclass('public.\"MB006001\"') is not null", Boolean.class);
        if (!Boolean.TRUE.equals(ok)) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Table MB006001 is missing. Run sql/03_mobile_utilities.sql on the database.");
        tableReady = true;
    }

    private Map<String, Object> record(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id").toString());
        m.put("name", r.get("image_name"));
        m.put("type", r.get("image_type"));
        m.put("path", r.get("image_path"));
        m.put("size", ((Number) r.get("file_size")).longValue());
        m.put("format", Objects.toString(r.get("file_format"), ""));
        m.put("dimensions", Objects.toString(r.get("dimensions"), ""));
        m.put("altText", Objects.toString(r.get("alt_text"), ""));
        m.put("platform", r.get("platform"));
        m.put("status", r.get("status"));
        java.sql.Timestamp t = (java.sql.Timestamp) (r.get("modified_on") != null ? r.get("modified_on") : r.get("created_on"));
        m.put("updatedAt", t == null ? "" : t.toInstant().atZone(IST).toLocalDate().toString());
        m.put("previewUrl", Objects.toString(r.get("thumb_data"), ""));
        m.put("hasFile", Boolean.TRUE.equals(r.get("has_file")));
        return m;
    }
}
