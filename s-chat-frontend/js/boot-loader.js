
(function () {
    const loader = document.getElementById("boot-loader");
    const video = document.getElementById("boot-video");

    if (!loader || !video) return;

    // Detect browsers where transparent WebM playback may not work.
    const ua = navigator.userAgent;

    const unsupportedBrowser =
        /iPhone|iPad|iPod/.test(ua) ||
        (/Safari/.test(ua) &&
         !/Chrome|Chromium|CriOS|Edg|Android/.test(ua));

    // Respect the user's reduced-motion preference.
    const reducedMotion = window.matchMedia(
        "(prefers-reduced-motion: reduce)"
    ).matches;

    if (unsupportedBrowser || reducedMotion) {
        loader.classList.add("is-fallback");
        return;
    }

    // Detect system theme.
    const systemTheme = window.matchMedia(
        "(prefers-color-scheme: dark)"
    );

    let currentSource = "";

    function isDarkMode() {
        const savedTheme =
            document.documentElement.getAttribute("data-theme");

        // Explicit website theme takes priority over system theme.
        if (savedTheme === "dark") return true;
        if (savedTheme === "light") return false;

        // Otherwise follow the device theme.
        return systemTheme.matches;
    }

    function updateLoader() {
        const source = isDarkMode()
            ? "/assets/loader/S-Chat-loading-transparent-white-dots.webm"
            : "/assets/loader/S-Chat-loading-transparent-dark-dots.webm";

        if (source === currentSource) return;

        currentSource = source;

        video.src = source;
        video.load();

        video.play().catch(function () {
            loader.classList.add("is-fallback");
        });
    }

    // Show the video only when playback actually begins.
    video.addEventListener("playing", function () {
        loader.classList.remove("is-fallback");
        loader.classList.add("is-playing");
    });

    // Fallback if the video cannot load.
    video.addEventListener("error", function () {
        loader.classList.add("is-fallback");
    });

    // Initial theme selection.
    updateLoader();

    // Watch for manual theme changes.
    const observer = new MutationObserver(updateLoader);

    observer.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ["data-theme"]
    });

    // Watch for device theme changes.
    systemTheme.addEventListener("change", updateLoader);


    // Hide the loader after the first route has finished rendering.
    window.addEventListener("schat:initial-route-resolved", function () {
        // Give the browser a moment to paint the actual page.
        requestAnimationFrame(function () {
            requestAnimationFrame(function () {
                loader.classList.add("is-exiting");

                // Remove the loader after the fade-out.
                setTimeout(function () {
                    loader.remove();
                }, 450);
            });
        });
    });


})();
