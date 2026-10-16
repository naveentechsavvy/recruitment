# Recruitment Platform – Backend (Spring Boot + MySQL)

Java 17+ · Maven · Spring Boot 3.5 · Spring Web · Spring Data JPA · Bean Validation · MySQL 8

## 1. Start MySQL
Any MySQL 8 server on port 3306 works. Windows: start the "MySQL80" service (Services app, or `net start MySQL80` in an Administrator terminal).
The database `recruitment_db` is created automatically on first start.

## 2. Set your database password (never hardcoded)
PowerShell:
    $env:DB_PASSWORD = "your-mysql-root-password"
Optional overrides: DB_HOST, DB_PORT, DB_NAME, DB_USERNAME (default root), UPLOAD_DIR, CORS_ALLOWED_ORIGINS.

## 3. Run
    mvn spring-boot:run
Or open the folder in IntelliJ / VS Code and run `RecruitmentApplication`.
Backend: http://localhost:8080  ·  Tables and the 9 standard recruitment processes are created on first start.

## 4. Run the Angular app (separate terminal, in the Angular folder)
    npm start          # http://localhost:4200

## API
| Method | URL | Purpose |
|---|---|---|
| POST | /api/clients | Create client (201) |
| GET | /api/clients | List clients |
| GET | /api/clients/{id} | Get one client |
| PUT | /api/clients/{id} | Update client |
| DELETE | /api/clients/{id} | Delete client (204) |
| POST | /api/clients/{id}/logo | Upload logo (multipart, field name `file`) |
| DELETE | /api/clients/{id}/logo | Remove logo |
| GET | /api/recruitment-processes | List available processes |
| POST | /api/clients/{id}/processes | Set the client's processes (replaces the list) |
| GET | /api/clients/{id}/processes | Get the client's processes |

## Quick test (Postman or curl)
    POST http://localhost:8080/api/clients
    {"name":"Samsung","description":"Samsung Recruitment"}

    GET  http://localhost:8080/api/recruitment-processes      (note the ids)

    POST http://localhost:8080/api/clients/1/processes
    {"processes":[
      {"processId":1,"enabled":true,"displayOrder":1},
      {"processId":2,"enabled":true,"displayOrder":2},
      {"processId":4,"enabled":true,"displayOrder":3}]}

    GET  http://localhost:8080/api/clients/1

Error example (POST /api/clients with {"name":""}) -> 400
    {"status":400,"error":"Bad Request","message":"Validation failed","fieldErrors":{"name":"Client name is required"}}


## Admin user invitations (added)

Admin users can assign a role to an email under **Admin Portal → Settings → User Management**. For an email without an account, the backend creates an inactive account and emails a single-use password setup link. The link expires after the configured number of hours (24 by default). The invitation token is stored as a SHA-256 hash and is marked used after password setup.

Configure the SMTP settings as environment variables before inviting new users:

- `MAIL_HOST` — SMTP host
- `MAIL_PORT` — SMTP port (defaults to `587`)
- `MAIL_USERNAME` — SMTP username
- `MAIL_PASSWORD` — SMTP password or app password
- `MAIL_FROM` — optional sender address (defaults to `MAIL_USERNAME`)
- `FRONTEND_URL` — frontend base URL (defaults to `http://localhost:4200`)
- `INVITATION_EXPIRY_HOURS` — invitation lifetime in hours (defaults to `24`)

Do not commit SMTP credentials to source control. Existing accounts are assigned the requested role without sending a new invitation. The `ADMIN` role grants full system access; other client roles are scoped to their assigned client and permitted stages.
