package com.abcbank.mobile_banking.service;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abcbank.mobile_banking.dto.request.UpdateProfileRequest;
import com.abcbank.mobile_banking.dto.response.ProfileResponse;

@Service
public class ProfileService {

    private final JdbcTemplate jdbc;

    public ProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ------------------------------------------------------------ GET */
    @Transactional(readOnly = true)
    public ProfileResponse get(String customerId) {
        return map(load(customerId, false));
    }

    /* ------------------------------------------------------------ PUT */
    @Transactional
    public ProfileResponse update(String customerId, UpdateProfileRequest req) {

        // Lock the row so two concurrent edits can't overwrite each other.
        Map<String, Object> cur = load(customerId, true);

        String title      = pick(req.title(),       str(cur, "title"));
        String firstName  = pick(req.firstName(),   str(cur, "first_name"));
        String middleName = pick(req.middleName(),  str(cur, "middle_name"));
        String lastName   = pick(req.lastName(),    str(cur, "last_name"));
        String dispName   = pick(req.displayName(), str(cur, "disp_name"));
        LocalDate dob     = req.dob() != null
                ? req.dob()
                : (cur.get("dob") == null ? null : ((java.sql.Date) cur.get("dob")).toLocalDate());

        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name is required.");
        }
        if (dob != null && Period.between(dob, LocalDate.now()).getYears() < 18) {
            throw new IllegalArgumentException("You must be at least 18 years old.");
        }

        jdbc.update("""
            UPDATE public."MB001001"
               SET title       = ?,
                   first_name  = ?,
                   middle_name = NULLIF(?, ''),
                   last_name   = NULLIF(?, ''),
                   disp_name   = ?,
                   dob         = ?,
                   modified_by = user_name      -- modified_on set by trigger
             WHERE user_id = ?
            """,
            title, firstName.trim(), blankToNull(middleName), blankToNull(lastName),
            dispName, dob, cur.get("user_id"));

        return map(load(customerId, false));
    }

    /* -------------------------------------------------------- helpers */
    private Map<String, Object> load(String customerId, boolean forUpdate) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Invalid customer authentication.");
        }
        try {
            return jdbc.queryForMap("""
                SELECT u.user_id, u.customer_no, u.title, u.first_name, u.middle_name,
                       u.last_name, u.full_name, u.disp_name, u.dob, u.email,
                       cr.registered_mobile AS mobile,
                       (u.verified_by IS NOT NULL) AS kyc_verified
                  FROM public."MB001001" u
                  JOIN public.mb001001cr cr ON cr.customer_id = u.customer_no
                 WHERE u.customer_no = ?
                   AND u.user_type   = 'CU'
                   AND u.status      = 'ACTIVE'
                """ + (forUpdate ? " FOR UPDATE OF u" : ""), customerId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Active customer profile not found.");
        }
    }

    private ProfileResponse map(Map<String, Object> r) {
        Object d = r.get("dob");
        return new ProfileResponse(
            str(r, "customer_no"),
            str(r, "title"),
            str(r, "first_name"),
            str(r, "middle_name"),
            str(r, "last_name"),
            str(r, "full_name"),
            str(r, "disp_name"),
            d == null ? null : ((java.sql.Date) d).toLocalDate(),
            str(r, "mobile"),
            str(r, "email"),
            Boolean.TRUE.equals(r.get("kyc_verified")) ? "Verified" : "Pending");
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    private static String pick(String incoming, String current) {
        return incoming != null ? incoming.trim() : current;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
