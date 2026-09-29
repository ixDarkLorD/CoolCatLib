# CoolCatLib docs

Source of the CoolCatLib documentation site, https://ixdarklord.github.io/CoolCatLib/, built with
[Material for MkDocs](https://squidfunk.github.io/mkdocs-material/). The mods' code is on the version branches
(`26.1.2`, `1.21`, `1.20.1`).

Every push to this branch rebuilds and publishes the site (`.github/workflows/docs.yml`).

Preview locally:

```bash
pip install -r requirements.txt
mkdocs serve
```

Pages are Markdown files under `docs/`; the navigation is in `mkdocs.yml`.
