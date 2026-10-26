// The Minecraft version switch in the top bar (overrides/partials/header.html): which version the reader plays, kept in
// their browser. Each page then shows what is true for it: content marked for another version is hidden, version tabs
// switch to it, and the version table marks its row. Runs on every page load, instant navigation too.
//
// Marking content for versions (any of mc-26, mc-121, mc-120 on the same element):
//   !!! note mc-120 "1.20.1"                          a note
//   <span class="mc-v mc-121 mc-120">...</span>       a part of a paragraph
//   class mc-only: shown only when one of its versions is picked (not for "All versions");
//   class mc-all: shown only for "All versions" (the wording that covers them all)
// Version tabs switch by their label, when it names only versions ("1.21.1 and 1.20.1"); tabs named after a loader
// ("Forge (1.21.1, 1.20.1)") are left as the reader set them.
(function () {
  const KEY = "coolcatlib-mc-version";

  function chosen() {
    try {
      return localStorage.getItem(KEY) || "";
    } catch (e) {
      return "";
    }
  }

  function remember(id) {
    try {
      localStorage.setItem(KEY, id);
    } catch (e) {
      // Private windows and blocked storage: the choice lasts until the page is left.
    }
  }

  // The versions a label is about: "26.1.2, 26.2 and 26.3", "1.21 – 1.21.1", "1.20.1".
  function versionsOf(label) {
    const found = [];
    if (/\b26\./.test(label)) found.push("26");
    if (/\b1\.21\b/.test(label)) found.push("121");
    if (/\b1\.20\.1\b/.test(label)) found.push("120");
    return found;
  }

  function apply(id) {
    document.documentElement.dataset.mc = id;
    // Marked content: shown for its versions, or when no version is picked.
    document.querySelectorAll(".mc-v, .admonition[class*='mc-'], details[class*='mc-']").forEach((element) => {
      const marked = [...element.classList].filter((name) => /^mc-\d+$/.test(name)).map((name) => name.slice(3));
      if (element.classList.contains("mc-all")) element.hidden = id !== "";
      else if (element.classList.contains("mc-only")) element.hidden = id === "" || !marked.includes(id);
      else element.hidden = id !== "" && marked.length > 0 && !marked.includes(id);
    });
    // Version tabs: the one for the picked version.
    if (id !== "") {
      document.querySelectorAll(".tabbed-set").forEach((set) => {
        const labels = set.querySelectorAll(":scope > .tabbed-labels > label");
        const inputs = set.querySelectorAll(":scope > input");
        labels.forEach((label, index) => {
          const text = label.textContent.trim();
          if (/^\d/.test(text) && versionsOf(text).includes(id) && inputs[index]) inputs[index].checked = true;
        });
      });
    }
    // The table of versions: the picked one's rows marked.
    document.querySelectorAll(".md-typeset table tr").forEach((row) => {
      const first = row.cells[0] ? row.cells[0].textContent.trim() : "";
      const versions = /^\d/.test(first) && first.length < 20 ? versionsOf(first) : [];
      row.classList.toggle("mc-row", id !== "" && versions.length === 1 && versions[0] === id);
    });
    document.querySelectorAll("[data-mc-picker]").forEach((select) => (select.value = id));
  }

  // The switch is in the top bar, which instant navigation keeps: it's wired once.
  function wire() {
    document.querySelectorAll("[data-mc-picker]").forEach((select) => {
      if (select.dataset.wired) return;
      select.dataset.wired = "1";
      select.addEventListener("change", () => {
        remember(select.value);
        apply(select.value);
      });
    });
  }

  function setup() {
    wire();
    apply(chosen());
  }

  if (window.document$ && window.document$.subscribe) window.document$.subscribe(setup);
  else document.addEventListener("DOMContentLoaded", setup);
})();
