// ============================================================
// Trust Guide – Misinformation Detector
// Frontend JavaScript for API Communication & DOM Updates
// ============================================================

const API_BASE_URL = "/api/claims";

// DOM Elements
const analyzeForm = document.getElementById("analyzeForm");
const claimInput = document.getElementById("claimInput");
const charCount = document.getElementById("charCount");
const analyzeBtn = document.getElementById("analyzeBtn");
const analyzeBtnText = document.getElementById("analyzeBtnText");
const analyzeSpinner = document.getElementById("analyzeSpinner");
const resetInputBtn = document.getElementById("resetInputBtn");

const dbStatusBadge = document.getElementById("dbStatusBadge");
const feedbackBanner = document.getElementById("feedbackBanner");

const resultSection = document.getElementById("resultSection");
const resultBadge = document.getElementById("resultBadge");
const resultClaimText = document.getElementById("resultClaimText");
const truePercentValue = document.getElementById("truePercentValue");
const falsePercentValue = document.getElementById("falsePercentValue");
const trueProgressBar = document.getElementById("trueProgressBar");
const falseProgressBar = document.getElementById("falseProgressBar");
const resultExplanation = document.getElementById("resultExplanation");
const resultTimestamp = document.getElementById("resultTimestamp");

const historyList = document.getElementById("historyList");
const historyLoading = document.getElementById("historyLoading");
const emptyHistoryState = document.getElementById("emptyHistoryState");
const refreshHistoryBtn = document.getElementById("refreshHistoryBtn");
const clearHistoryBtn = document.getElementById("clearHistoryBtn");

// Initialize page on load
document.addEventListener("DOMContentLoaded", () => {
    loadHistory();
    setupEventListeners();
});

/**
 * Sets up event listeners for buttons, sample chips, and form submission.
 */
function setupEventListeners() {
    // Update character counter as user types
    claimInput.addEventListener("input", updateCharacterCount);

    // Fill textarea when a sample button is clicked
    const sampleButtons = document.querySelectorAll(".chip-btn");
    sampleButtons.forEach((button) => {
        button.addEventListener("click", () => {
            claimInput.value = button.getAttribute("data-sample");
            updateCharacterCount();
            claimInput.focus();
        });
    });

    // Clear input text button
    resetInputBtn.addEventListener("click", () => {
        claimInput.value = "";
        updateCharacterCount();
        hideFeedback();
        claimInput.focus();
    });

    // Handle Analyze Claim form submission
    analyzeForm.addEventListener("submit", handleAnalyzeSubmit);

    // Refresh history button
    refreshHistoryBtn.addEventListener("click", () => {
        hideFeedback();
        loadHistory();
    });

    // Clear all history button
    clearHistoryBtn.addEventListener("click", handleClearAllHistory);
}

/**
 * Updates the character counter below the textarea.
 */
function updateCharacterCount() {
    const currentLength = claimInput.value.length;
    charCount.textContent = `${currentLength} / 3000`;
}

/**
 * Sends the entered news claim to the Java backend for analysis and database storage.
 */
async function handleAnalyzeSubmit(event) {
    event.preventDefault();
    hideFeedback();

    const claimText = claimInput.value.trim();

    // 1. Validate that the input is not empty
    if (!claimText) {
        showFeedback("Please enter a news claim or headline before clicking Analyze Claim.", "error");
        claimInput.focus();
        return;
    }

    if (claimText.length > 3000) {
        showFeedback("Your text is too long. Please keep the claim under 3,000 characters.", "error");
        return;
    }

    setAnalyzeLoading(true);

    try {
        const response = await fetch(`${API_BASE_URL}/analyze`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ claimText: claimText })
        });

        const data = await parseJsonSafely(response);

        if (!response.ok) {
            const errorMessage = (data && data.error)
                ? data.error
                : "Failed to analyze the claim. Please check if the backend server is running.";
            showFeedback(errorMessage, "error");
            return;
        }

        setDatabaseBadge(true);

        // Display the result and True/False percentages in the Assessment Result card
        displayAnalysisResult(data);
        showFeedback(
            `Claim analyzed: ${data.truePercentage}% True / Reliable vs. ${data.falsePercentage}% False / Misleading Risk (Saved to Database).`,
            "success"
        );

        // Automatically refresh the history list from the database
        await loadHistory();
    } catch (error) {
        setDatabaseBadge(false);
        showFeedback(
            "Unable to reach the Java backend server. Please make sure the Spring Boot application is running on port 8080.",
            "error"
        );
    } finally {
        setAnalyzeLoading(false);
    }
}

/**
 * Displays the returned assessment result and True/False percentages on the page.
 */
function displayAnalysisResult(claimRecord) {
    const truePct = typeof claimRecord.truePercentage === "number" ? claimRecord.truePercentage : 50;
    const falsePct = typeof claimRecord.falsePercentage === "number" ? claimRecord.falsePercentage : (100 - truePct);

    resultClaimText.textContent = `"${claimRecord.claimText}"`;
    resultExplanation.textContent = claimRecord.explanation;
    resultTimestamp.textContent = `Analyzed on ${formatDateTime(claimRecord.createdAt)}`;

    truePercentValue.textContent = `${truePct}%`;
    falsePercentValue.textContent = `${falsePct}%`;
    trueProgressBar.style.width = `${truePct}%`;
    falseProgressBar.style.width = `${falsePct}%`;

    resultBadge.textContent = claimRecord.result;
    resultBadge.className = "status-badge " + getBadgeClass(claimRecord.result);

    resultSection.classList.remove("hidden");
}

/**
 * Fetches all saved claim records from the backend and renders them in the Recent History section.
 */
async function loadHistory() {
    historyLoading.classList.remove("hidden");
    emptyHistoryState.classList.add("hidden");

    try {
        const response = await fetch(API_BASE_URL);
        const data = await parseJsonSafely(response);

        if (!response.ok) {
            setDatabaseBadge(false);
            const errorMessage = (data && data.error)
                ? data.error
                : "Could not load history from the database.";
            showFeedback(errorMessage, "error");
            historyList.innerHTML = "";
            clearHistoryBtn.disabled = true;
            return;
        }

        setDatabaseBadge(true);
        renderHistoryList(Array.isArray(data) ? data : []);
    } catch (error) {
        setDatabaseBadge(false);
        showFeedback(
            "Cannot connect to the backend server to load history. Please ensure the Spring Boot server is running.",
            "error"
        );
        historyList.innerHTML = "";
        clearHistoryBtn.disabled = true;
    } finally {
        historyLoading.classList.add("hidden");
    }
}

/**
 * Renders the list of history records inside the history container.
 */
function renderHistoryList(records) {
    historyList.innerHTML = "";

    if (records.length === 0) {
        emptyHistoryState.classList.remove("hidden");
        clearHistoryBtn.disabled = true;
        return;
    }

    emptyHistoryState.classList.add("hidden");
    clearHistoryBtn.disabled = false;

    records.forEach((record) => {
        const truePct = typeof record.truePercentage === "number" ? record.truePercentage : 50;
        const falsePct = typeof record.falsePercentage === "number" ? record.falsePercentage : (100 - truePct);

        const item = document.createElement("div");
        item.className = "history-item";

        const topRow = document.createElement("div");
        topRow.className = "history-item-top";

        const metaGroup = document.createElement("div");
        metaGroup.className = "history-meta";

        const badge = document.createElement("span");
        badge.className = "status-badge " + getBadgeClass(record.result);
        badge.textContent = record.result;

        const truePill = document.createElement("span");
        truePill.className = "pct-pill pct-pill-true";
        truePill.textContent = `True: ${truePct}%`;

        const falsePill = document.createElement("span");
        falsePill.className = "pct-pill pct-pill-false";
        falsePill.textContent = `False: ${falsePct}%`;

        const dateSpan = document.createElement("span");
        dateSpan.className = "history-date";
        dateSpan.textContent = formatDateTime(record.createdAt);

        metaGroup.appendChild(badge);
        metaGroup.appendChild(truePill);
        metaGroup.appendChild(falsePill);
        metaGroup.appendChild(dateSpan);

        const deleteBtn = document.createElement("button");
        deleteBtn.type = "button";
        deleteBtn.className = "btn btn-danger-outline btn-sm";
        deleteBtn.textContent = "Delete";
        deleteBtn.addEventListener("click", () => handleDeleteRecord(record.id));

        topRow.appendChild(metaGroup);
        topRow.appendChild(deleteBtn);

        const claimPara = document.createElement("p");
        claimPara.className = "history-claim";
        claimPara.textContent = record.claimText;

        const explanationPara = document.createElement("p");
        explanationPara.className = "history-explanation";
        explanationPara.textContent = record.explanation;

        item.appendChild(topRow);
        item.appendChild(claimPara);
        item.appendChild(explanationPara);

        historyList.appendChild(item);
    });
}

/**
 * Deletes a single history record by ID after user confirmation.
 */
async function handleDeleteRecord(id) {
    const confirmed = window.confirm("Are you sure you want to delete this record from the history?");
    if (!confirmed) {
        return;
    }

    hideFeedback();

    try {
        const response = await fetch(`${API_BASE_URL}/${id}`, {
            method: "DELETE"
        });

        const data = await parseJsonSafely(response);

        if (!response.ok) {
            const errorMessage = (data && data.error)
                ? data.error
                : "Failed to delete the selected history record.";
            showFeedback(errorMessage, "error");
            return;
        }

        showFeedback("History record deleted from the database.", "success");
        await loadHistory();
    } catch (error) {
        showFeedback("Unable to connect to the server while deleting the record.", "error");
    }
}

/**
 * Clears all history records after user confirmation.
 */
async function handleClearAllHistory() {
    const confirmed = window.confirm(
        "Are you sure you want to clear all saved analysis history from the database? This action cannot be undone."
    );
    if (!confirmed) {
        return;
    }

    hideFeedback();

    try {
        const response = await fetch(API_BASE_URL, {
            method: "DELETE"
        });

        const data = await parseJsonSafely(response);

        if (!response.ok) {
            const errorMessage = (data && data.error)
                ? data.error
                : "Failed to clear history.";
            showFeedback(errorMessage, "error");
            return;
        }

        showFeedback("All analysis history records have been cleared from the database.", "success");
        await loadHistory();
    } catch (error) {
        showFeedback("Unable to connect to the server while clearing history.", "error");
    }
}

/**
 * Updates the header badge showing whether the database is connected.
 */
function setDatabaseBadge(isConnected) {
    if (!dbStatusBadge) {
        return;
    }
    if (isConnected) {
        dbStatusBadge.textContent = "Database Connected";
        dbStatusBadge.className = "db-badge db-connected";
    } else {
        dbStatusBadge.textContent = "Database Offline";
        dbStatusBadge.className = "db-badge db-disconnected";
    }
}

/**
 * Returns the appropriate CSS badge class for a result string.
 */
function getBadgeClass(result) {
    if (result && result.toLowerCase().includes("misleading")) {
        return "badge-misleading";
    }
    return "badge-verify";
}

/**
 * Formats an ISO timestamp string into a friendly local date and time.
 */
function formatDateTime(isoString) {
    if (!isoString) {
        return "Just now";
    }
    const parsedDate = new Date(isoString);
    if (isNaN(parsedDate.getTime())) {
        return isoString;
    }
    return parsedDate.toLocaleString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit"
    });
}

/**
 * Toggles the loading spinner on the Analyze Claim button.
 */
function setAnalyzeLoading(isLoading) {
    analyzeBtn.disabled = isLoading;
    if (isLoading) {
        analyzeBtnText.textContent = "Analyzing...";
        analyzeSpinner.classList.remove("hidden");
    } else {
        analyzeBtnText.textContent = "Analyze Claim";
        analyzeSpinner.classList.add("hidden");
    }
}

/**
 * Shows a friendly error or success message at the top of the page.
 */
function showFeedback(message, type) {
    feedbackBanner.textContent = message;
    feedbackBanner.className = "feedback-banner " + (type === "success" ? "feedback-success" : "feedback-error");
    feedbackBanner.classList.remove("hidden");
}

/**
 * Hides the feedback banner.
 */
function hideFeedback() {
    feedbackBanner.classList.add("hidden");
    feedbackBanner.textContent = "";
}

/**
 * Safely parses JSON from an HTTP response without throwing if the body is empty.
 */
async function parseJsonSafely(response) {
    try {
        return await response.json();
    } catch (e) {
        return null;
    }
}
