// Makes the floating S-Tech AI orb draggable anywhere in the viewport.
// A plain click/tap (no movement past the threshold) still opens the Gemini link;
// a drag repositions it and suppresses the click. Position persists across pages.
(function () {
    const DRAG_THRESHOLD = 6; // px of movement before it counts as a drag, not a click
    const POSITION_KEY = "schat_orb_position";

    const orb = document.getElementById("stech-ai-orb");
    const link = document.getElementById("gemini-orb-link");
    if (!orb) return;

    orb.style.left = "";
    orb.style.bottom = "20px";
    orb.style.left = "20px";
    orb.style.top = "";
    orb.style.right = "";

    // Restore a saved position, clamped to the current viewport in case the
    // window was resized smaller since it was last saved.
    try {
        const saved = JSON.parse(localStorage.getItem(POSITION_KEY) || "null");
        if (saved) {
            applyPosition(clamp(saved.x, saved.y));
        }
    } catch { /* ignore corrupt saved state */ }

    let dragging = false;
    let moved = false;
    let startX = 0, startY = 0;
    let originX = 0, originY = 0;

    orb.addEventListener("pointerdown", (e) => {
        dragging = true;
        moved = false;
        startX = e.clientX;
        startY = e.clientY;
        const rect = orb.getBoundingClientRect();
        originX = rect.left;
        originY = rect.top;
        orb.setPointerCapture(e.pointerId);
    });

    orb.addEventListener("pointermove", (e) => {
        if (!dragging) return;
        const dx = e.clientX - startX;
        const dy = e.clientY - startY;

        if (!moved && Math.hypot(dx, dy) > DRAG_THRESHOLD) {
            moved = true;
        }
        if (moved) {
            applyPosition(clamp(originX + dx, originY + dy));
        }
    });

    function endDrag(e) {
        if (!dragging) return;
        dragging = false;
        if (moved) {
            const rect = orb.getBoundingClientRect();
            localStorage.setItem(POSITION_KEY, JSON.stringify({ x: rect.left, y: rect.top }));
        }
    }
    orb.addEventListener("pointerup", endDrag);
    orb.addEventListener("pointercancel", endDrag);

    // Only treat it as a tap (and fire the callback) if the pointerdown/up
    // sequence never crossed the drag threshold.
    if (link) {
        link.addEventListener("click", (e) => {
            if (moved) {
                e.preventDefault();
                return;
            }
            if (typeof window.STECH_ORB_ON_TAP === "function") {
                window.STECH_ORB_ON_TAP();
            }
        });
    }

    function clamp(x, y) {
        const size = orb.offsetWidth || 100;
        const maxX = window.innerWidth - size - 8;
        const maxY = window.innerHeight - size - 8;
        return {
            x: Math.min(Math.max(x, 8), Math.max(maxX, 8)),
            y: Math.min(Math.max(y, 8), Math.max(maxY, 8))
        };
    }

    function applyPosition(pos) {
        orb.style.left = pos.x + "px";
        orb.style.top = pos.y + "px";
        orb.style.bottom = "auto";
        orb.style.right = "auto";
    }

    window.addEventListener("resize", () => {
        const rect = orb.getBoundingClientRect();
        applyPosition(clamp(rect.left, rect.top));
    });
})();