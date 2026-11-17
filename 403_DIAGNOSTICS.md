# Backend 403 handling update

## Changes
- Kept the JWT filter and token validation in place.
- Kept `GET /themes/**` and `GET /banks/*/theme` public for branding/bootstrap data.
- Kept all other endpoints protected by JWT authentication.
- Allowed CORS preflight `OPTIONS` requests.
- Added consistent JSON responses for unauthenticated (401) and authenticated-but-forbidden (403) requests.
- Added a server-side warning for authorization denials. The warning includes method, request path, principal, granted authorities, and denial reason; it never logs the bearer token.

## Important scope limitation
The supplied archive contains only `AuthController` and `RequestHandlingController`. It does not contain controllers/services for `/themes/**`, `/banks/{bankCode}/theme`, or `/dashboard/bank`. Therefore this update cannot change any `@PreAuthorize`, controller-level permission check, tenant/bank ownership check, or service-level rule in those missing classes. The 403 log will help identify whether the denial is occurring in Spring Security; if the log is absent, inspect the corresponding controller/service response path.

## Build verification
The source was reviewed, but Maven compilation could not be completed in this environment because the Maven wrapper could not download Maven from Maven Central. Please run from the extracted folder:

```powershell
.\mvnw.cmd -f portal-api\pom.xml clean compile
```

Do not replace the production JWT secret or disable authentication to work around a 403.
