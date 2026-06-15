function connectStreams(root) {
  const scope = root instanceof Element ? root : document;
  scope.querySelectorAll("[data-stream-url]:not([data-stream-connected])").forEach((run) => {
    run.dataset.streamConnected = "true";
    const target = run.querySelector("[data-stream-target]");
    const source = new EventSource(run.dataset.streamUrl);

    source.addEventListener("fragment", (event) => {
      applyServerFragment(run, target, event.data);
      run.scrollIntoView({ block: "nearest", behavior: "smooth" });
    });

    source.addEventListener("done", () => {
      source.close();
      run.classList.add("run--done");
    });

    source.addEventListener("error", () => {
      if (source.readyState === EventSource.CLOSED) {
        return;
      }
      target.insertAdjacentHTML(
        "beforeend",
        '<section class="notice notice--error"><strong>Stream interrupted</strong><p>The browser lost the event stream for this run.</p></section>'
      );
      source.close();
    });
  });
}

function applyServerFragment(run, streamTarget, html) {
  const template = document.createElement("template");
  template.innerHTML = html.trim();

  template.content.querySelectorAll("[data-replace-target]").forEach((node) => {
    const selector = node.dataset.replaceTarget;
    const destination = run.querySelector(selector);
    if (destination) {
      node.removeAttribute("data-replace-target");
      destination.replaceWith(node);
    }
  });

  template.content.querySelectorAll("[data-append-target]").forEach((node) => {
    const selector = node.dataset.appendTarget;
    const destination = run.querySelector(selector);
    if (destination) {
      const emptyState = destination.querySelector(".muted");
      if (emptyState) {
        emptyState.remove();
      }
      node.removeAttribute("data-append-target");
      destination.appendChild(node);
    }
  });

  if (template.content.childNodes.length > 0) {
    streamTarget.append(template.content);
    if (window.htmx) {
      htmx.process(streamTarget);
    }
  }
}

document.addEventListener("DOMContentLoaded", () => connectStreams(document));
document.body.addEventListener("htmx:afterSwap", (event) => connectStreams(event.target));
