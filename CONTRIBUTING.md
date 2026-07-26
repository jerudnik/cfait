# Contributing

Thank you for contributing to Cfait. Keep changes aligned with the shared Rust
core so TUI, GUI, and Android stay consistent. Read [SPECS.md](./SPECS.md)
before changing behavior, syntax, or settings.

## How to build

Release CI builds both desktop binaries with:

```bash
cargo build --release --features gui
```

Outputs: `target/release/cfait` (TUI) and `target/release/cfait-gui` (GUI).

## How to run tests

Match the Forgejo CI checks in `.forgejo/workflows/test_roll.yml`:

```bash
cargo fmt --all -- --check
cargo clippy --all-features --all-targets --locked -- -D warnings
cargo test --all-features --all-targets --locked --verbose
```

Optional pre-commit formatting (runs `cargo fmt --all`):

```bash
git config core.hooksPath .githooks
```

## Pull request conventions

- Prefer PRs on [Codeberg](https://codeberg.org/trougnouf/cfait); GitHub PRs are
  accepted but must be ported manually for CI.
- Put business logic in the Rust core (`src/model`, `src/store.rs`,
  `src/controller.rs`); keep UIs thin.
- Update `SPECS.md` in the same PR when behavior, settings, or syntax change.
- Document user-facing features in the in-app help (`src/help.rs`,
  `src/tui/view.rs`, `src/gui/view/help.rs`, Android `HelpScreen.kt`).
- Ensure the build and test commands above pass before opening a PR.
