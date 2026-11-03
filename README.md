# recruitment



# Velzenix Nova – Applicant Tracking System (ATS)

## Project Overview

Velzenix Nova ATS is a multi-client recruitment management application that manages the complete recruitment lifecycle, from candidate application to offer acceptance and pre-onboarding.

It provides a centralized Admin Portal and separate client recruitment portals with role-based access and configurable recruitment workflows.

## Key Features

- Client and portal management
- User and role management
- Requirement management
- Candidate application and resume screening
- Recruitment stage management
- Candidate history and approval tracking
- Hold and resume functionality
- Offer letter PDF generation and email delivery
- Offer acceptance and rejection
- OTP-based pre-onboarding
- Candidate document submission and HR review
- Correction requests and final approval

## Technology Stack

| Component | Technology |
|---|---|
| Frontend | Angular 20 |
| Backend | Java 17, Spring Boot 3.5.6 |
| Database | MySQL |
| Security | Spring Security, JWT |
| ORM | Spring Data JPA, Hibernate |
| Email | JavaMailSender |
| PDF | OpenPDF |

## Project Structure

```text
recruitment/
├── README.md
├── docs/
├── recruitment-admin/
└── recruitment-backend/
```

## Prerequisites

- Java 17
- Maven
- Node.js and npm
- MySQL
- Git

## Setup and Run

### 1. Clone the Repository

```bash
git clone <YOUR_REPOSITORY_URL>
cd recruitment
```

### 2. Create the Database

```sql
CREATE DATABASE recruitment_db;
```

Configure database credentials and other required environment variables in the backend configuration.

### 3. Start Backend

```bash
cd recruitment-backend
mvn clean install
mvn spring-boot:run
```

Backend: `http://localhost:8080`

### 4. Start Frontend

Open another terminal:

```bash
cd recruitment-admin
npm install
npm start
```

Frontend: `http://localhost:4200`

## Security Notes

- Never commit passwords, API secrets, or SMTP credentials.
- Do not upload private candidate documents or resumes to GitHub.
- Keep environment files and private storage directories out of version control.
- Validate permissions in the backend.


---

##Commands
Backend:

mvn clean install

$env:DB_PASSWORD = 'Gowtham@123'
mvn spring-boot:run "-Dspring-boot.run.profiles=local"


Frontend:

npm install
ng serve


