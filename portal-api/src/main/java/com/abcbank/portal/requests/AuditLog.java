package com.abcbank.portal.requests;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Writes to the audit table MB005001 (skipped if the table does not exist yet). */
@Component
public class AuditLog {
    private final JdbcTemplate db;
    private volatile Boolean exists;

    public AuditLog(JdbcTemplate db) { this.db = db; }

    public void record(Caller who, HttpServletRequest http, String action, String module, Long recordId, String detail) {
        Boolean e = exists;
        if (e == null) {
            e = db.queryForObject("select to_regclass('public.\"MB005001\"') is not null", Boolean.class);
            exists = e;
        }
        if (!Boolean.TRUE.equals(e)) return;
        db.update("insert into public.\"MB005001\" (actor_id, actor_name, actor_role, ip, action, module, record_id, detail) values (?,?,?,?,?,?,?,?)",
                who == null ? null : who.id(), who == null ? "system" : who.name(), who == null ? "SYSTEM" : who.role(),
                http == null ? null : http.getRemoteAddr(), action, module, recordId,
                detail == null ? null : detail.substring(0, Math.min(detail.length(), 500)));
    }
}
