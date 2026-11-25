// History-API router. Routes are matched in order; `a[data-link]` clicks are intercepted for SPA navigation.
const routes = [];
let notFoundHandler = null;
export const notFound = (h) => { notFoundHandler = h; };
export function route(pattern, handler) {
  const keys = [];
  const re = new RegExp("^" + pattern.replace(/:([a-zA-Z]+)/g, (_, k) => { keys.push(k); return "([^/]+)"; }) + "/?$");
  routes.push({ re, keys, handler });
}
export function navigate(path, { replace = false } = {}) {
  if (path === location.pathname + location.search) return resolve();
  history[replace ? "replaceState" : "pushState"]({}, "", path);
  return resolve();
}

let initialRouteResolved = false;

export async function resolve() {
  const path = location.pathname;

  try {
    for (const r of routes) {
      const m = path.match(r.re);

      if (m) {
        const params = {};
        r.keys.forEach((k, i) => {
          params[k] = decodeURIComponent(m[i + 1]);
        });

        window.scrollTo(0, 0);

        return await r.handler(params);
      }
    }

    return await notFoundHandler?.({});
  } finally {
    if (!initialRouteResolved) {
      initialRouteResolved = true;

      window.dispatchEvent(
        new Event("schat:initial-route-resolved")
      );
    }
  }
}

window.addEventListener("popstate", resolve);
document.addEventListener("click", (e) => {
  const a = e.target.closest("a[data-link]");
  if (!a || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || a.target === "_blank") return;
  e.preventDefault(); navigate(a.getAttribute("href"));
});
