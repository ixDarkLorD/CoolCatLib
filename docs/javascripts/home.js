// The home page's motion (docs/index.md): the screen-effects showcase in the hero changing picture, sections rising
// into view as they're scrolled to, and the feature cards lit where the pointer is. With reduced motion asked for,
// only the light under the pointer stays. Runs on every page load, instant navigation too; does nothing off the home page.
(function () {
  const still = window.matchMedia("(prefers-reduced-motion: reduce)");
  let timer = 0;
  let watcher = null;

  // The hero's pictures, one after another, the caption naming the effect.
  function slides() {
    const figure = document.querySelector("[data-ccl-slides]");
    if (!figure || still.matches) return;
    const pictures = [...figure.querySelectorAll("img")];
    const label = figure.querySelector("[data-ccl-slide-label]");
    let at = 0;
    timer = window.setInterval(() => {
      pictures[at].classList.remove("is-on");
      at = (at + 1) % pictures.length;
      pictures[at].classList.add("is-on");
      if (label) label.textContent = pictures[at].dataset.label;
    }, 2600);
  }

  // What's under the hero fades and rises in the first time it's scrolled to.
  function reveal() {
    if (still.matches || !("IntersectionObserver" in window)) return;
    const parts = document.querySelectorAll(
      ".md-content__inner > :is(h2, .ccl-stats, .ccl-halves, .grid, .admonition, .highlight, .annotate, .mermaid, table, .md-typeset__scrollwrap)"
    );
    watcher = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("is-in");
        watcher.unobserve(entry.target);
      });
    }, { rootMargin: "0px 0px -8% 0px" });
    parts.forEach((part) => {
      part.classList.add("ccl-rise");
      watcher.observe(part);
    });
  }

  // The cards are lit from where the pointer is over them.
  function light() {
    document.querySelectorAll(".ccl-features > ul > li, .ccl-half").forEach((card) => {
      card.addEventListener("pointermove", (event) => {
        const box = card.getBoundingClientRect();
        card.style.setProperty("--x", `${event.clientX - box.left}px`);
        card.style.setProperty("--y", `${event.clientY - box.top}px`);
      });
    });
  }

  function setup() {
    window.clearInterval(timer);
    if (watcher) watcher.disconnect();
    watcher = null;
    if (!document.querySelector(".ccl-hero")) return;
    slides();
    reveal();
    light();
  }

  if (window.document$ && window.document$.subscribe) window.document$.subscribe(setup);
  else document.addEventListener("DOMContentLoaded", setup);
})();
