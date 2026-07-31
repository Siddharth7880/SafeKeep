<div align="center">
  <h1>SafeKeep</h1>
  <p><strong>Digital Dead Man's Switch — Secure Digital Legacy Platform</strong></p>

  <p>
    <a href="#features">Features</a> •
    <a href="#tech-stack">Tech Stack</a> •
    <a href="#getting-started">Getting Started</a> •
    <a href="#architecture">Architecture</a> •
    <a href="#security">Security</a>
  </p>
</div>

---

## 🛡️ Overview

SafeKeep is a secure, automated platform designed to ensure your digital legacy is passed on safely. Operating as a "Dead Man's Switch," SafeKeep requires you to check in periodically. If a check-in is missed, the platform automatically initiates a secure release process to deliver your encrypted vault items to designated recipients.

## ✨ Features

- **Secure Data Vault**: Store encrypted messages, files, and credentials.
- **Automated Dead Man's Switch**: Configurable check-in intervals with grace periods.
- **Multi-Channel Notifications**: Reminders sent via Email and SMS (Twilio).
- **Grace Period & Escalation**: Multiple warning stages before initiating data release.
- **Zero-Knowledge Architecture Principles**: Data is encrypted at rest using AES.
- **Recipient Management**: Securely designate and manage beneficiaries.
- **Detailed Audit Logging**: Immutable logs of all significant actions for security auditing.

## 💻 Tech Stack

### Backend
- **Java 21 & Spring Boot 3.3.2**
- **Spring Security & JWT**: For robust authentication and authorization.
- **PostgreSQL**: Relational database for persistent storage.
- **Flyway**: Database migration and versioning.
- **Quartz Scheduler**: Precision scheduling for check-ins and automated tasks.
- **Bucket4j**: API rate limiting to prevent abuse.
- **Twilio**: SMS integration for urgent notifications.

### Frontend
- **React 19 & Vite**: Blazing fast, modern frontend framework.
- **Zustand**: Lightweight and scalable state management.
- **React Query (TanStack)**: Powerful asynchronous state management.
- **Framer Motion**: Smooth, declarative animations.

### DevOps & Infrastructure
- **Docker & Docker Compose**: Containerized deployment for consistent environments.

## 🚀 Getting Started

### Prerequisites
- Java 21+
- Node.js 20+
- PostgreSQL 16+
- Docker (optional, for containerized setup)

### Option 1: Docker Compose (Recommended)

1. Clone the repository:
   ```bash
   git clone https://github.com/Siddharth7880/SafeKeep.git
   cd SafeKeep
   ```

2. Configure environment variables (create a `.env` file at the root):
   ```env
   DB_PASSWORD=your_secure_password
   JWT_SECRET=your_super_secret_jwt_key
   RELEASE_TOKEN_SECRET=your_release_token_key
   MAIL_USERNAME=your_email@example.com
   MAIL_PASSWORD=your_email_password
   ```

3. Start the application:
   ```bash
   docker-compose up -d --build
   ```

4. Access the application:
   - Frontend: `http://localhost:5173`
   - Backend API: `http://localhost:8080/api`

### Option 2: Manual Setup

#### Backend Setup
1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Configure your `application.properties` using `application.properties.example` as a template.
3. Run the application:
   ```bash
   ./mvnw clean spring-boot:run
   ```

#### Frontend Setup
1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Create a `.env` file based on `.env.example`.
4. Start the development server:
   ```bash
   npm run dev
   ```

## 🔐 Security & Privacy

SafeKeep is built with security as a top priority:
- All sensitive vault items are encrypted at rest using industry-standard AES encryption.
- JWTs are strictly validated and heavily secured.
- APIs are protected against brute-force attacks using Bucket4j rate limiting.
- Granular role-based access control (RBAC).

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
