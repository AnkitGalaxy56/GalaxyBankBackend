# User Management API update

This update adds the `/user-management` API backed by the supplied PostgreSQL tables:

- `public."MB001001"` — user master records.
- `public."MB001002"` — password history / assigned password hashes.

## API routes

- `GET /api/v1/user-management?page=1&pageSize=8&search=...` — paginated user list, including CU, STAFF and ADMIN records.
- `GET /api/v1/user-management/{userId}` — one user.
- `PUT /api/v1/user-management/{userId}` — update editable user-master fields.
- `PATCH /api/v1/user-management/{userId}` — status / authorization update.
- `PATCH /api/v1/user-management/{userId}/authorize` — authorize a pending user.
- `POST /api/v1/user-management/{userId}/assign-password` — assign/reset a password.

All routes require an authenticated administrator. Passwords are BCrypt encoded and inserted into `MB001002`; `must_reset` is set to `true`. The plaintext password is not stored.

## Database assumptions

The two tables must already exist exactly as supplied. `user_id` and `password_id` are treated as application-managed numeric keys; the password insert serializes ID allocation with a PostgreSQL advisory transaction lock because the supplied DDL does not declare a sequence/default for `password_id`.

The frontend's existing `.env` / environment configuration should be retained when replacing backend source files. No unrelated frontend pages or backend controllers were changed.
