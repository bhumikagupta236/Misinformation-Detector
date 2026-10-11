# Trust Guide – Misinformation Detector

**Think before you trust.**

**Trust Guide** is a beginner-friendly, full-stack web application built for a college Problem-Based Learning (PBL) project. It allows users to enter a news headline, social media forward, or short paragraph and receive an initial baseline assessment of whether the text appears **Potentially Misleading** or **Needs Verification**, while saving and managing analysis history in a **MySQL** database.

> **Educational Note:** This application uses a transparent, keyword-based heuristic approach in Java to identify sensational or clickbait phrasing. It does not claim to definitively prove whether a news story is true or false, and it is not a trained machine-learning fact-checking model.

---

## 1. What Trust Guide Does

- **Analyzes News Claims:** Checks entered news claims against a documented baseline list of sensational or clickbait expressions (case-insensitive) and excessive punctuation patterns.
- **Provides Nuanced Preliminary Results:**
  - **Potentially Misleading:** Returned when one or more sensational/clickbait indicators are found, along with an explanation of which expressions triggered the warning.
  - **Needs Verification:** Returned when no suspicious keywords are detected, reminding the user that the absence of clickbait wording is not proof that a claim is true.
- **Stores History in MySQL:** Saves the original claim text, assessment result, explanation, and timestamp in the `news_claims` table inside the `trust_guide` MySQL database.
- **Manages History:** Displays saved records ordered newest first, and allows users to delete individual records or clear all history from the database.

---

## 2. Technologies Used

- **Frontend:** HTML5, CSS3, Vanilla JavaScript (Fetch API)
- **Backend:** Java, Spring Boot (Spring Web REST API, Spring Data JPA)
- **Database:** MySQL (`trust_guide` database, `news_claims` table)
- **Build Tool:** Apache Maven (includes Maven Wrapper `mvnw` / `mvnw.cmd`)

---

## 3. Project Folder Structure

```text
Trust guides/
├── pom.xml
├── schema.sql
├── README.md
├── mvnw
├── mvnw.cmd
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── datastellers/
        │           └── detector/
        │               ├── MisinformationDetectorApplication.java
        │               ├── NewsClaim.java
        │               ├── NewsClaimRepository.java
        │               ├── NewsClaimService.java
        │               └── NewsClaimController.java
        └── resources/
            ├── application.properties
            └── static/
                ├── index.html
                ├── style.css
                └── script.js
```

---

## 4. Prerequisites

1. **Java Development Kit (JDK):** Java 21 or newer (compatible with JDK 21–26).
   - Check with: `java -version`
2. **MySQL Server:** MySQL 8.0+ (or MariaDB / XAMPP MySQL running on port `3306`).
3. **Maven:** Included via the project's Maven Wrapper (`.\mvnw.cmd`), or an installed Apache Maven 3.9+.

---

## 5. How to Create the MySQL Database and Table

Make sure your MySQL Server is running, then open **MySQL Workbench** or the **MySQL Command Line Client** and run the SQL commands from [`schema.sql`](schema.sql):

```sql
CREATE DATABASE IF NOT EXISTS trust_guide
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE trust_guide;

CREATE TABLE IF NOT EXISTS news_claims (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_text TEXT NOT NULL,
    result VARCHAR(100) NOT NULL,
    explanation TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

*(Note: The application is also configured with `createDatabaseIfNotExist=true` and `spring.jpa.hibernate.ddl-auto=update`, so once MySQL is running and your credentials are set, Spring Boot can automatically create the `trust_guide` database and `news_claims` table on startup.)*

---

## 6. How to Configure Database Credentials

Database settings are located in `src/main/resources/application.properties`:

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/trust_guide?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:YOUR_MYSQL_PASSWORD}
```

You can configure your credentials in **either of two ways**:

### Option A: Set Environment Variables in PowerShell (Recommended)
```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_actual_mysql_password"
```

### Option B: Update `application.properties`
Open `src/main/resources/application.properties` and replace `YOUR_MYSQL_PASSWORD` with your local MySQL `root` password.

---

## 7. How to Build and Run the Spring Boot Application

Open a terminal (PowerShell or Command Prompt) in the project root folder and run:

### Using the included Maven Wrapper (Windows PowerShell):
```powershell
.\mvnw.cmd spring-boot:run
```

### Or using an installed Maven binary:
```powershell
mvn spring-boot:run
```

---

## 8. Which URL to Open in the Browser

Once the terminal shows `Started MisinformationDetectorApplication`, open your browser and visit:

```text
http://localhost:8080
```

---

## 9. How to Test Analyzing, Saving, Viewing, and Deleting History

1. **Analyze a Sensational Claim:**
   - Click the **Sensational Claim** example chip (or type `"Shocking truth revealed! Share immediately!!!"`) and click **Analyze Claim**.
   - Verify that the **Assessment Result** card displays **Potentially Misleading** along with the matched indicators and educational note.
2. **Analyze a Neutral Headline:**
   - Click the **Neutral News Headline** chip and click **Analyze Claim**.
   - Verify that the result displays **Needs Verification** and explains why the absence of suspicious keywords does not prove a claim is true.
3. **Verify MySQL Persistence:**
   - Refresh the browser page (`F5`). Both analyzed claims remain in the **Recent History** section ordered newest first.
   - Or run `SELECT * FROM trust_guide.news_claims;` in MySQL to view the stored rows directly.
4. **Delete a Single Record:**
   - Click **Delete** on any history card and confirm the prompt. Verify that the record is removed from the list and from MySQL.
5. **Clear All History:**
   - Click **Clear History**, confirm the prompt, and verify that all records are deleted and the empty history message is displayed.

---

## 10. Common Errors and How to Resolve Them

| Problem | Cause | Solution |
| :--- | :--- | :--- |
| **`Database connection error: Unable to reach the MySQL database`** | MySQL Server is not running, or the password in `application.properties` / `DB_PASSWORD` does not match your MySQL password. | Start MySQL Server (e.g., via Windows Services or MySQL Workbench) and verify `DB_USERNAME` and `DB_PASSWORD`. |
| **`Port 8080 was already in use`** | Another instance of Spring Boot or another service is using port 8080. | Stop the other process or change `server.port=8081` in `src/main/resources/application.properties`. |
| **`'mvn' is not recognized`** | Maven is not added to the Windows system `PATH`. | Use the included Maven Wrapper command `.\mvnw.cmd spring-boot:run` instead of `mvn`. |
| **`Access denied for user 'root'@'localhost'`** | Incorrect MySQL password configured. | Set `$env:DB_PASSWORD="your_password"` in PowerShell before running the app, or update `spring.datasource.password` in `application.properties`. |
