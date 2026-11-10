// ============================================================
// Trust Guide – Shared Authentication & Navigation Helper
// Manages session checking, header navigation, and route guards
// ============================================================

/**
 * Checks authentication status with the backend.
 * If requireAuth is true and user is not logged in, redirects to login.html.
 * If redirectIfLoggedIn is true and user IS logged in, redirects to dashboard.html.
 */
async function initAuth(options = {}) {
    const { requireAuth = false, redirectIfLoggedIn = false } = options;

    try {
        const response = await fetch("/api/auth/current-user");
        const data = await response.json();

        if (data && data.loggedIn) {
            // User is logged in
            if (redirectIfLoggedIn) {
                window.location.href = "dashboard.html";
                return null;
            }
            renderNavForUser(data.user);
            return data.user;
        } else {
            // User is not logged in
            if (requireAuth) {
                window.location.href = "login.html";
                return null;
            }
            renderNavForGuest();
            return null;
        }
    } catch (e) {
        console.warn("Could not check authentication status:", e);
        if (requireAuth) {
            window.location.href = "login.html";
        }
        return null;
    }
}

/**
 * Renders header navigation for an authenticated user.
 */
function renderNavForUser(user) {
    const navContainer = document.getElementById("headerNav");
    if (!navContainer) return;

    const currentPath = window.location.pathname;

    navContainer.innerHTML = `
        <a href="dashboard.html" class="nav-link ${currentPath.includes('dashboard') ? 'active' : ''}">Dashboard</a>
        <a href="history.html" class="nav-link ${currentPath.includes('history') ? 'active' : ''}">History</a>
        <a href="analytics.html" class="nav-link ${currentPath.includes('analytics') ? 'active' : ''}">Analytics</a>
        <span class="user-greeting">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
            </svg>
            ${escapeHtml(user.fullName || "User")}
        </span>
        <button type="button" id="logoutBtn" class="btn btn-secondary btn-sm">Logout</button>
    `;

    const logoutBtn = document.getElementById("logoutBtn");
    if (logoutBtn) {
        logoutBtn.addEventListener("click", handleLogout);
    }
}

/**
 * Renders header navigation for a guest (unauthenticated).
 */
function renderNavForGuest() {
    const navContainer = document.getElementById("headerNav");
    if (!navContainer) return;

    const currentPath = window.location.pathname;

    navContainer.innerHTML = `
        <a href="index.html" class="nav-link ${currentPath.endsWith('index.html') || currentPath.endsWith('/') ? 'active' : ''}">Home</a>
        <a href="login.html" class="nav-link ${currentPath.includes('login') ? 'active' : ''}">Login</a>
        <a href="register.html" class="btn btn-primary btn-sm">Register</a>
    `;
}

/**
 * Logs out the user and redirects to login.html.
 */
async function handleLogout() {
    try {
        await fetch("/api/auth/logout", { method: "POST" });
    } catch (e) {
        // ignore network error on logout
    }
    window.location.href = "login.html";
}

/**
 * Helper to escape HTML characters in user names.
 */
function escapeHtml(str) {
    if (!str) return "";
    return str
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
