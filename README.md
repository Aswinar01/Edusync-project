# EduSync - AI Document Verification Platform

EduSync is a modern, production-ready educational platform designed to seamlessly fact-check, manage, and update PDF documents using cutting-edge AI (Groq & Gemini APIs). It features a beautiful Google-like user interface, robust security, and an optimized Spring Boot backend.

## 🚀 Key Features

*   **Intelligent PDF Verification:** Parses uploaded PDFs and uses AI to identify factual inaccuracies and generate corrected documents.
*   **Google-Style Interface:** A responsive, polished UI featuring glassmorphism, smooth micro-animations, and strict Material Design principles.
*   **Offline-First Capabilities:** Employs `IndexedDB` and `localStorage` to allow seamless "Guest" usage without creating an account.
*   **Production-Grade Security:**
    *   **BCrypt Password Hashing:** Secure password storage.
    *   **Cryptographic OTPs:** Salted and hashed Email OTP verification.
    *   **Session Hardening:** `HttpOnly`, `SameSite=Lax`, and `Secure` session cookies.
    *   **Global Exception Handling:** Prevents stack trace leakage to the frontend.
*   **High Performance:** Optimized PostgreSQL queries with B-Tree indexing, strict file descriptor management, and GZIP compression.

## 🛠️ Tech Stack

*   **Backend:** Java 17, Spring Boot 3.3, Spring Data JPA
*   **Database:** PostgreSQL (Optimized for Supabase)
*   **Frontend:** HTML5, Vanilla JavaScript (ES6 Strict Mode), Vanilla CSS
*   **PDF Processing:** Apache PDFBox, OpenHTMLToPDF, Jsoup
*   **AI Integration:** Groq (Mixtral 8x7B) and Google Gemini

## 💻 How to Run Locally

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/yourusername/edusync.git
    cd edusync
    ```

2.  **Configure Environment Variables:**
    Create a `.env` file in the root directory (or configure your IDE) with the following variables:
    ```env
    # AI API Keys
    GEMINI_API_KEY=your_gemini_api_key_here
    GROQ_API_KEY=your_groq_api_key_here

    # Database Configuration (Local PostgreSQL or Supabase)
    DB_URL=jdbc:postgresql://localhost:5432/edusync
    DB_USERNAME=postgres
    DB_PASSWORD=your_password

    # Email Configuration for OTP (e.g., Gmail App Password)
    SPRING_MAIL_HOST=smtp.gmail.com
    SPRING_MAIL_PORT=587
    SPRING_MAIL_USERNAME=your_email@gmail.com
    SPRING_MAIL_PASSWORD=your_app_password
    ```

3.  **Run the Application:**
    Use Maven to build and run the Spring Boot application:
    ```bash
    mvn clean install
    mvn spring-boot:run
    ```

4.  **Access the Application:**
    Open your browser and navigate to `http://localhost:8080`.

## ☁️ How to Deploy (Render + Supabase)

EduSync is designed to be easily deployed on modern cloud platforms.

1.  **Database Setup (Supabase):**
    *   Create a new project on [Supabase](https://supabase.com/).
    *   Obtain your PostgreSQL connection string, username, and password.
    
2.  **Application Deployment (Render):**
    *   Create a new **Web Service** on [Render](https://render.com/).
    *   Connect your GitHub repository.
    *   **Environment:** Select `Java` (Maven).
    *   **Build Command:** `mvn clean package -DskipTests`
    *   **Start Command:** `java -jar target/pdfupdater-0.0.1-SNAPSHOT.jar`
    *   **Environment Variables:** Add all the variables from the `.env` file (e.g., `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `GROQ_API_KEY`, etc.). Ensure `SECURE_COOKIES=true` is set.

## 🔐 Security Highlights

During the final production review, the following security hardening measures were implemented:
*   Transitioned from SHA-256 to **BCrypt** for robust password hashing.
*   Enforced **PDF-only** file uploads with strict MIME-type and header validation (`X-Content-Type-Options: nosniff`).
*   Implemented a **Global Exception Handler** to sanitize all server errors into standard JSON, eliminating HTML stack trace leaks.
*   Enforced **Strict Mode (`"use strict";`)** and stripped all debug logs from production JavaScript assets.

---
*Built with ❤️ for modern education.*
