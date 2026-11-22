// Wires the signup and login forms to the backend. Include on
// S-chat-signup.html and S-chat-log-in.html only.
(function () {
  function showAlert(el, message, type) {
    if (!el) return;
    el.textContent = message;
    el.classList.remove("error", "success");
    el.classList.add("show", type);
  }

  function setLoading(button, loading) {
    if (!button) return;
    button.disabled = loading;
    button.dataset.originalText = button.dataset.originalText || button.textContent;
    button.textContent = loading ? "Please wait..." : button.dataset.originalText;
  }

  function clearFieldErrors(form) {
    form.querySelectorAll(".field-invalid").forEach(el => el.classList.remove("field-invalid"));
    form.querySelectorAll(".field-error-text").forEach(el => el.remove());
  }

  function postAuthDestination() {
    const fallback = "S-chat-home.html";
    const requested = new URLSearchParams(window.location.search).get("next");
    if (!requested) return fallback;
    try {
      const destination = new URL(requested, window.location.origin);
      if (destination.origin !== window.location.origin) return fallback;
      const path = destination.pathname.replace(/^\//, "");
      if (!/^(S-chat-[a-z-]+\.html|public-profile\.html)$/.test(path)) return fallback;
      return path + destination.search + destination.hash;
    } catch {
      return fallback;
    }
  }

  function markFieldError(input, message) {
    input.classList.add("field-invalid");
    const note = document.createElement("small");
    note.className = "field-error-text";
    note.textContent = message;
    input.insertAdjacentElement("afterend", note);
  }

  // ---------- Signup ----------
  const signupForm = document.getElementById("signup-form");
  if (signupForm) {
    const alertBox = document.getElementById("auth-alert");
    const submitBtn = signupForm.querySelector("button[type='submit']");

    signupForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      clearFieldErrors(signupForm);

      const username = document.getElementById("signup-username").value.trim();
      const email = document.getElementById("signup-email").value.trim();
      const password = document.getElementById("signup-password").value;
      const termsAccepted = document.getElementById("terms-accepted")?.checked === true;

      let hasError = false;
      if (username.length < 3) {
        markFieldError(document.getElementById("signup-username"), "Username must be at least 3 characters");
        hasError = true;
      }
      if (!/^\S+@\S+\.\S+$/.test(email)) {
        markFieldError(document.getElementById("signup-email"), "Enter a valid email address");
        hasError = true;
      }
      if (password.length < 8 || !/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/\d/.test(password)) {
        markFieldError(
          document.getElementById("signup-password"),
          "Min 8 characters, with an uppercase letter, lowercase letter and a number"
        );
        hasError = true;
      }
      if (!termsAccepted) {
        showAlert(alertBox, "Please review and agree to the Terms and Conditions and Privacy Policy.", "error");
        document.getElementById("terms-accepted")?.focus();
        return;
      }
      if (hasError) return;

      setLoading(submitBtn, true);
      try {
        const result = await SChat.apiFetch("/auth/signup", {
          method: "POST",
          skipAuth: true,
          body: JSON.stringify({ username, email, password, termsAccepted })
        });
        SChat.Auth.saveSession(result);
        showAlert(alertBox, "Account created! Redirecting…", "success");
        setTimeout(() => (window.location.href = "S-chat-home.html"), 600);
      } catch (err) {
        showAlert(alertBox, err.message, "error");
      } finally {
        setLoading(submitBtn, false);
      }
    });
  }

  // ---------- Login ----------
  const loginForm = document.getElementById("login-form");
  if (loginForm) {
    const alertBox = document.getElementById("auth-alert");
    const submitBtn = loginForm.querySelector("button[type='submit']");

    loginForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      clearFieldErrors(loginForm);

      const usernameOrEmail = document.getElementById("login-identifier").value.trim();
      const password = document.getElementById("login-password").value;

      if (!usernameOrEmail || !password) {
        showAlert(alertBox, "Enter your username/email and password", "error");
        return;
      }

      setLoading(submitBtn, true);
      try {
        const result = await SChat.apiFetch("/auth/login", {
          method: "POST",
          skipAuth: true,
          body: JSON.stringify({ usernameOrEmail, password })
        });
        SChat.Auth.saveSession(result);
        window.location.href = postAuthDestination();
      } catch (err) {
        showAlert(alertBox, err.message, "error");
      } finally {
        setLoading(submitBtn, false);
      }
    });
  }

  // Redirect signed-in users away from auth pages
  if ((signupForm || loginForm) && SChat.Auth.isLoggedIn()) {
    window.location.href = postAuthDestination();
  }
})();
