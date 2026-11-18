# Allianza Portal API (isolated backend addition)

This is a separate Spring Boot application under `portal-api/`. The existing mobile-banking backend files were left untouched. It connects to the same PostgreSQL database and uses the schema in the supplied `ani.sql` dump.

## Run

1. Copy `.env.example` to `.env` in the `portal-api` working directory and fill in database credentials.
2. Set `PORTAL_JWT_SECRET` to a random secret of at least 32 characters. Do not use the example value in production.
3. Set `PORTAL_LOGIN_OTP` to the fixed OTP you want to use for this environment.
4. Run `mvn spring-boot:run` from this directory (Java 21).
5. API base URL: `http://localhost:8081/api/v1`.

## Authentication

- `POST /auth/login` accepts `{ "loginId": "MB001001.user_name", "password": "..." }`.
- User identity is loaded from `public."MB001001"` by `user_name`.
- The latest password hash is read from `public."MB001002".password_hash`; it must be a BCrypt hash.
- `POST /auth/verify-otp` accepts `{ "challengeId": "...", "otp": "..." }`.
- A signed JWT is issued only after OTP verification and saved to `MB001001.token`.
- Every authenticated request validates the JWT signature/expiry and checks that the exact token is still stored against the active user. Logout clears that DB token.
- `GET /auth/profile` and `POST /auth/logout` require a bearer token.

## Request Handling API

For mapped modules, the API provides list, view-by-id, create, edit, and decision endpoints:

- `GET /request-handling/{module}?page=0&pageSize=25&search=&status=`
- `GET /request-handling/{module}/{id}`
- `POST /request-handling/{module}`
- `PUT /request-handling/{module}/{id}`
- `PATCH /request-handling/{module}/{id}/decision` with `{ "status": "AUTHORISED", "remarks": "..." }`

Mappings grounded in the supplied SQL:

| Frontend module | PostgreSQL table |
|---|---|
| `rtgs-neft-imps`, `payments` | `MB004001` |
| `cheque-book` | `MB004002` |
| `stop-payment` | `MB004003` |
| `fd-opening` | `MB004004` |
| `rd-opening` | `MB004005` |
| `loan-emi-payment` | `MB004006` |
| `locker-application` | `MB004007` |
| `standing-instructions` | `MB004008` |
| `fresh-loan-application` | `MB004010` |

The supplied SQL dump has no dedicated request tables for `opening-accounts`, `closing-accounts`, or `lead-management`; these are intentionally not pointed at unrelated tables. Their actual table definitions are needed to enable those modules safely. Validation module prefixes are removed in the frontend service and use the corresponding module API.

## Access rules

The API uses `user_role` from `MB001001` for role claims. `ADMIN` can access all request rows. Other roles are scoped to their `branch` value using `branch_code`. New request rows created by staff are assigned their branch from the signed token; callers cannot override it.

## Notes

- OTP challenge state is held in process memory. Use a shared cache/database table before running multiple API instances.
- The portal API does not modify the original backend's Spring configuration or routes.
- Do not expose `.env`, JWT secrets, or real passwords in source control.

## Mobile request review + session re-verification

New mobile review endpoints are implemented in `MobileRequestReviewController`; the session credential re-check endpoint is in `SessionVerificationController`. Apply `sql/05_mobile_requests.sql` before enabling the three request screens. The frontend uses these APIs through `src/services/mobile-requests.service.ts`.

Session warning behavior: 3 minutes idle, then a 3-minute warning countdown. The warning dialog blocks Escape/backdrop dismissal. The user must re-enter the currently authenticated Login ID and password and press **I am here**; `/auth/verify-session` checks that Login ID belongs to the current authenticated user and validates the password hash. At zero, the portal logs out.

See `MOBILE_REQUEST_TABLE_FIELDS.md` for the database column list.
