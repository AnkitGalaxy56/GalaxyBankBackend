
package com.abcbank.portal.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/mobile-requests")
public class MobileRequestReviewController {

    private final JdbcTemplate db;

    public MobileRequestReviewController(JdbcTemplate db) {
        this.db = db;
    }

    private record Actor(long id, String branch, boolean admin) {}

    private Actor actor(Authentication authentication) {

        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        long id = Long.parseLong(authentication.getName());

        Map<String, Object> user = db.queryForMap(
            "select user_type, branch " +
            "from public.\"MB001001\" " +
            "where user_id = ?",
            id
        );

        String userType = Objects.toString(user.get("user_type"), "");

        return new Actor(
            id,
            Objects.toString(user.get("branch"), ""),
            userType.equalsIgnoreCase("ADMIN")
                || userType.equalsIgnoreCase("SUPER_ADMIN")
        );
    }

    private String scope(Actor actor, String alias) {
        return actor.admin ? "" : " and " + alias + ".branch_code = ?";
    }

    // =========================================================
    // LIST REQUESTS
    // GET /mobile-requests/{module}
    // =========================================================

    @GetMapping({"/{module}", "/{module}/"})
    public List<Map<String, Object>> list(
        @PathVariable String module,
        Authentication authentication
    ) {

        Actor actor = actor(authentication);

        String branchScope = scope(actor, "t");

        List<Object> args = new ArrayList<>();

        if (!actor.admin) {
            args.add(actor.branch);
        }

        switch (module) {

            // =================================================
            // NOMINATION
            // =================================================

            case "nomination":

                return db.queryForList(
                    "select " +
                    "t.id, " +
                    "coalesce(u.full_name, t.customer_no) customer, " +
                    "t.account_no account, " +
                    "t.nominee_name nominee, " +
                    "t.relationship, " +
                    "t.date_of_birth dob, " +
                    "t.mobile_no mobile, " +
                    "t.created_on submitted, " +
                    "t.req_status status, " +
                    "t.branch_code branch " +

                    "from public.\"MB004014\" t " +

                    "left join public.\"MB001001\" u " +
                    "on u.customer_no = t.customer_no " +

                    "where 1=1 " +
                    branchScope +

                    "order by t.created_on desc",

                    args.toArray()
                );

            // =================================================
            // KYC
            // =================================================

            case "kyc":

                return db.queryForList(
                    "select " +
                    "p.id, " +
                    "coalesce(u.full_name, p.customer_no) customer, " +
                    "p.account_no account, " +
                    "count(d.id) docs, " +
                    "p.created_on submitted, " +
                    "p.kyc_status status, " +
                    "p.current_step stage, " +
                    "p.customer_no, " +
                    "a.branch_code branch " +

                    "from public.\"MB004016\" p " +

                    "left join public.\"MB004015\" d " +
                    "on d.customer_no = p.customer_no " +
                    "and d.account_no = p.account_no " +

                    "left join public.\"MB001001\" u " +
                    "on u.customer_no = p.customer_no " +

                    "left join public.\"MB002003\" a " +
                    "on a.customer_no = p.customer_no " +
                    "and a.account_no = p.account_no " +

                    "where 1=1 " +
                    (actor.admin
                        ? ""
                        : "and a.branch_code = ? ") +

                    "group by " +
                    "p.id, " +
                    "u.full_name, " +
                    "p.customer_no, " +
                    "p.account_no, " +
                    "p.created_on, " +
                    "p.kyc_status, " +
                    "p.current_step, " +
                    "a.branch_code " +

                    "order by p.created_on desc",

                    actor.admin
                        ? new Object[]{}
                        : new Object[]{actor.branch}
                );

            // =================================================
            // POSITIVE PAY
            // =================================================

            case "positive-pay":

                return db.queryForList(
                    "select " +
                    "t.id, " +
                    "coalesce(u.full_name, t.customer_no) customer, " +
                    "t.account_no account, " +
                    "t.cheque_no chequeNo, " +
                    "t.cheque_date chequeDate, " +
                    "t.cheque_amount amount, " +
                    "t.payee_name payee, " +
                    "t.remarks, " +
                    "t.created_on submitted, " +
                    "t.req_status status, " +
                    "t.account_type accountType, " +
                    "t.customer_confirmed declaration, " +
                    "t.branch_code branch " +

                    "from public.\"MB004013\" t " +

                    "left join public.\"MB001001\" u " +
                    "on u.customer_no = t.customer_no " +

                    "where 1=1 " +
                    branchScope +

                    "order by t.created_on desc",

                    args.toArray()
                );

            default:

                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Unknown mobile request module."
                );
        }
    }

    // =========================================================
    // REQUEST DETAILS
    // GET /mobile-requests/{module}/{id}
    // =========================================================

    @GetMapping("/{module}/{id}")
    public Map<String, Object> detail(
        @PathVariable String module,
        @PathVariable long id,
        Authentication authentication
    ) {

        Actor actor = actor(authentication);

        String sql;
        Object[] args;

        // =====================================================
        // NOMINATION DETAIL
        // =====================================================

        if (module.equals("nomination")) {

            sql =
                "select * " +
                "from public.\"MB004014\" " +
                "where id = ? " +
                (actor.admin
                    ? ""
                    : "and branch_code = ?");

            args = actor.admin
                ? new Object[]{id}
                : new Object[]{id, actor.branch};

        }

        // =====================================================
        // POSITIVE PAY DETAIL
        // =====================================================

        else if (module.equals("positive-pay")) {

            sql =
                "select * " +
                "from public.\"MB004013\" " +
                "where id = ? " +
                (actor.admin
                    ? ""
                    : "and branch_code = ?");

            args = actor.admin
                ? new Object[]{id}
                : new Object[]{id, actor.branch};

        }

        // =====================================================
        // KYC DETAIL
        // =====================================================

        else if (module.equals("kyc")) {

            sql =
                "select " +
                "p.*, " +
                "( " +
                    "select json_agg(d) " +
                    "from public.\"MB004015\" d " +
                    "where d.customer_no = p.customer_no " +
                    "and d.account_no = p.account_no " +
                ") documents " +

                "from public.\"MB004016\" p " +

                "join public.\"MB002003\" a " +
                "on a.customer_no = p.customer_no " +
                "and a.account_no = p.account_no " +

                "where p.id = ? " +
                (actor.admin
                    ? ""
                    : "and a.branch_code = ?");

            args = actor.admin
                ? new Object[]{id}
                : new Object[]{id, actor.branch};

        }

        else {

            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Unknown module."
            );
        }

        try {

            return db.queryForMap(sql, args);

        } catch (Exception e) {

            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Request not found."
            );
        }
    }

    // =========================================================
    // VERIFY REQUEST
    // PATCH /mobile-requests/{module}/{id}/verify
    // =========================================================

    @PatchMapping("/{module}/{id}/verify")
    @Transactional
    public Map<String, Object> verify(
        @PathVariable String module,
        @PathVariable long id,
        @RequestBody(required = false) Map<String, Object> body,
        Authentication authentication
    ) {

        return decide(
            module,
            id,
            "APPROVED",
            null,
            authentication
        );
    }

    // =========================================================
    // REJECT REQUEST
    // PATCH /mobile-requests/{module}/{id}/reject
    // =========================================================

    @PatchMapping("/{module}/{id}/reject")
    @Transactional
    public Map<String, Object> reject(
        @PathVariable String module,
        @PathVariable long id,
        @RequestBody Map<String, String> body,
        Authentication authentication
    ) {

        String reason = body == null
            ? ""
            : Objects.toString(body.get("reason"), "");

        return decide(
            module,
            id,
            "REJECTED",
            reason,
            authentication
        );
    }

    // =========================================================
    // APPROVE / REJECT LOGIC
    // =========================================================

    private Map<String, Object> decide(
        String module,
        long id,
        String status,
        String reason,
        Authentication authentication
    ) {

        Actor actor = actor(authentication);

        String branchScope = actor.admin
            ? ""
            : " and branch_code = ?";

        // =====================================================
        // KYC APPROVE / REJECT
        // =====================================================

        if (module.equals("kyc")) {

            List<Object> args = new ArrayList<>();

            args.add(status);
            args.add(actor.id);
            args.add(status.equals("APPROVED"));
            args.add(status.equals("APPROVED"));
            args.add(reason);
            args.add(id);

            String scope = actor.admin
                ? ""
                : " and exists ( " +
                  "select 1 " +
                  "from public.\"MB002003\" ac " +
                  "where ac.account_no = p.account_no " +
                  "and ac.customer_no = p.customer_no " +
                  "and ac.branch_code = ? " +
                  ")";

            if (!actor.admin) {
                args.add(actor.branch);
            }

            String sql =
                "update public.\"MB004016\" p " +

                "set kyc_status = ?, " +
                "verified_by = ?, " +
                "verified_on = now(), " +
                "final_approved = ?, " +
                "review_completed = ?, " +
                "rejection_reason = ?, " +
                "current_step = 4, " +
                "modified_on = now() " +

                "where p.id = ? " +
                "and upper(trim(coalesce(p.kyc_status, ''))) not in ('APPROVED', 'REJECTED', 'COMPLETED') " +

                scope;

            int updated = db.update(
                sql,
                args.toArray()
            );

            if (updated == 0) {

                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "KYC request not pending, not found, or outside your branch."
                );
            }
        }

        // =====================================================
        // NOMINATION / POSITIVE PAY APPROVE / REJECT
        // =====================================================

        else if (
            module.equals("nomination")
            || module.equals("positive-pay")
        ) {

            String table = module.equals("nomination")
                ? "MB004014"
                : "MB004013";

            // Only a rejection writes the rejection reason. Verification should
            // not touch this optional field, avoiding unnecessary DB constraints.
            String rejectColumn =
                module.equals("positive-pay") && status.equals("REJECTED")
                    ? ", reject_reason = ?"
                    : "";

            String pendingStatuses =
                " and upper(trim(coalesce(req_status, ''))) " +
                "in ('PENDING', 'PENDING_REVIEW', 'PENDING REVIEW', 'IN_PROGRESS', 'SUBMITTED')";

            List<Object> args = new ArrayList<>();

            args.add(status);
            args.add(actor.id);
            args.add(actor.id);

            if (!rejectColumn.isEmpty()) {
                args.add(reason);
            }

            args.add(id);
            args.add(actor.id);

            if (!actor.admin) {
                args.add(actor.branch);
            }

            String sql =
                "update public.\"" + table + "\" " +

                "set req_status = ?, " +
                "verified_by = ?, " +
                "verified_on = now(), " +
                "modified_by = ?, " +
                "modified_on = now() " +

                rejectColumn +

                "where id = ? " +
                pendingStatuses +
                "and created_by <> ? " +

                branchScope;

            int updated = db.update(
                sql,
                args.toArray()
            );

            if (updated == 0) {

                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Request not pending, not found, or outside your branch."
                );
            }
        }

        // =====================================================
        // INVALID MODULE
        // =====================================================

        else {

            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Unknown module."
            );
        }

        return Map.of(
            "status", status,
            "message", "Request decision saved"
        );
    }
}
