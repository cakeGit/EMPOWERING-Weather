// Debug modal open/close handlers

export function installDebugModalHandlers() {
    document.addEventListener("DOMContentLoaded", () => {
        const debugBtn = document.getElementById("debugBtn");
        const debugModal = document.getElementById("debugModal");
        const closeDebug = document.getElementById("closeDebug");
        if (!debugBtn || !debugModal) return;
        function open() {
            debugModal.classList.remove("hidden");
            debugModal.setAttribute("aria-hidden", "false");
        }
        function close() {
            debugModal.classList.add("hidden");
            debugModal.setAttribute("aria-hidden", "true");
        }
        debugBtn.addEventListener("click", open);
        closeDebug && closeDebug.addEventListener("click", close);
        debugModal.addEventListener("click", (e) => {
            if (e.target === debugModal) close();
        });
    });
}
