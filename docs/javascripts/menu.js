// The menu button in the top bar (overrides/partials/header.html). On narrow screens it is the drawer's label and opens
// the drawer; on wide ones it folds the menu away and back, the choice kept in the reader's browser.
(function () {
  const KEY = "coolcatlib-menu-closed";
  const wide = window.matchMedia("(min-width: 76.25em)");

  function closed() {
    try {
      return localStorage.getItem(KEY) === "1";
    } catch (e) {
      return false;
    }
  }

  function remember(value) {
    try {
      localStorage.setItem(KEY, value ? "1" : "0");
    } catch (e) {
      // Private windows and blocked storage: the choice lasts until the page is left.
    }
  }

  // The bar stays through instant navigation, so the button is wired once.
  function wire() {
    document.body.classList.toggle("ccl-closed", closed());
    const button = document.querySelector(".ccl-header__menu");
    if (!button || button.dataset.wired) return;
    button.dataset.wired = "1";
    button.addEventListener("click", (event) => {
      if (!wide.matches) return;
      event.preventDefault();
      remember(document.body.classList.toggle("ccl-closed"));
    });
    button.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      event.preventDefault();
      button.click();
    });
  }

  if (window.document$ && window.document$.subscribe) window.document$.subscribe(wire);
  else document.addEventListener("DOMContentLoaded", wire);
})();
