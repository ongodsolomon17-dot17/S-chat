// Accessible Terms modal: summary -> full terms -> back to summary.
(function () {
  const modal = document.getElementById("terms-modal");
  if (!modal) return;
  const summary = document.getElementById("terms-summary");
  const full = document.getElementById("terms-full");
  const open = document.getElementById("open-terms");
  const close = document.getElementById("close-terms");
  const more = document.getElementById("see-more-terms");
  const back = document.getElementById("back-to-terms");
  const checkbox = document.getElementById("terms-accepted");

  function showSummary() {
    summary.hidden = false;
    full.hidden = true;
  }
  function openModal() {
    showSummary();
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    document.body.classList.add("modal-open");
    close.focus();
  }
  function closeModal() {
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
    document.body.classList.remove("modal-open");
    open.focus();
  }

  open?.addEventListener("click", openModal);
  close?.addEventListener("click", closeModal);
  more?.addEventListener("click", () => {
    summary.hidden = true;
    full.hidden = false;
    full.scrollTop = 0;
    back.focus();
  });
  back?.addEventListener("click", () => {
    showSummary();
    summary.scrollTop = 0;
    more.focus();
  });
  modal.addEventListener("click", event => {
    if (event.target === modal) closeModal();
  });
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && modal.classList.contains("is-open")) closeModal();
  });
  checkbox?.addEventListener("change", () => {
    if (checkbox.checked) checkbox.setCustomValidity("");
  });
})();
