(() => {
  const outputs = [...document.querySelectorAll("[data-request-output]")];
  document.querySelectorAll("[data-clock]").forEach((node) => {
    node.textContent = new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" }).format(new Date());
  });

  function report(message, kind = "neutral") {
    outputs.forEach((output) => {
      output.textContent = message;
      output.dataset.kind = kind;
    });
  }

  async function send(method, path, button, conditional = false) {
    if (button) button.disabled = true;
    const headers = conditional ? { "If-None-Match": '"honeyphone-status-v1"' } : {};
    report(`${method} ${path} · procesando solicitud`, "neutral");
    try {
      const response = await fetch(path, {
        method,
        headers,
        cache: "no-store",
        credentials: "omit",
        redirect: "manual",
      });
      const category = Math.floor(response.status / 100);
      const kind = category === 2 || response.status === 304 ? "success" : category === 4 ? "warning" : category === 5 ? "error" : "neutral";
      const detail = response.status === 304
        ? "representación sin cambios"
        : response.status === 101
          ? "cambio de protocolo solicitado"
          : response.statusText || "respuesta recibida";
      report(`${method} ${path} → ${response.status} ${detail}`, kind);
    } catch (_) {
      const detail = path === "/upgrade"
        ? "el navegador cerró la negociación 101; el intento quedó registrado"
        : "no se pudo leer la respuesta; el intento quedó registrado localmente";
      report(`${method} ${path} → ${detail}`, "warning");
    } finally {
      if (button) button.disabled = false;
    }
  }

  function probeWebSocket(button) {
    if (button) button.disabled = true;
    report("GET /upgrade · negociando protocolo WebSocket", "neutral");
    let opened = false;
    let finished = false;
    let socket;
    const finish = (message, kind) => {
      if (finished) return;
      finished = true;
      report(message, kind);
      if (button) button.disabled = false;
    };
    try {
      socket = new WebSocket(`ws://${location.host}/upgrade`);
      const timeout = window.setTimeout(() => {
        finish("GET /upgrade · no se completó la negociación; el intento quedó registrado", "warning");
        socket.close();
      }, 2500);
      socket.addEventListener("open", () => {
        opened = true;
        window.clearTimeout(timeout);
        finish("GET /upgrade → 101 Switching Protocols", "success");
        socket.close(1000, "probe complete");
      }, { once: true });
      socket.addEventListener("error", () => {
        window.clearTimeout(timeout);
        if (!opened) finish("GET /upgrade · el navegador rechazó la negociación", "warning");
      }, { once: true });
      socket.addEventListener("close", () => {
        window.clearTimeout(timeout);
        if (opened && !finished) finish("GET /upgrade → 101 Switching Protocols", "success");
        else if (!finished) finish("GET /upgrade · conexión cerrada", "warning");
      }, { once: true });
    } catch (_) {
      finish("GET /upgrade · este navegador no admite la negociación", "warning");
    }
  }

  document.querySelectorAll("[data-request]").forEach((button) => {
    button.addEventListener("click", () => {
      if (button.dataset.path === "/upgrade") probeWebSocket(button);
      else send(button.dataset.method || "GET", button.dataset.path || "/status", button, button.dataset.etag === "status");
    });
  });

  document.querySelectorAll("[data-login-form]").forEach((form) => {
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      send("POST", "/login", form.querySelector("button[type=submit]"));
    });
  });
})();
