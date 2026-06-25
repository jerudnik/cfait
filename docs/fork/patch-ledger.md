# Downstream patch ledger

Track downstream patches that may need to be upstreamed, watched, or retired.
Permanent fork behavior can stay here too, but every temporary shim must have a
retirement condition and validation command.

cfait is packaged with no source patches: the fork carries Nix packaging and the
4nix maintenance surface only. The rows below record the packaging decisions a
future maintainer needs to keep correct, not source diffs.

| Patch | Class | Status | Upstream ref | Retire condition | Validation |
|---|---|---|---|---|---|
| Nix dependency-cache stability (no per-commit stamp in `buildDepsOnly`) | `distro(nix)` | `permanent-downstream` | none | cfait has no build-time git stamp; `buildMeta` is empty. Keep it empty so the crane dependency layer stays cache-stable. Never introduce per-commit env vars into `commonArgs`. | `nix run github:jerudnik/4nix-utilities#fork-doctor` (git-stamp check must PASS) |
| Build both bins with `--features gui` | `distro(nix)` | `permanent-downstream` | none | The default feature set is `tui` only; the package must pass `--features gui --bin cfait --bin cfait-gui` or the GUI binary is never produced. | `nix flake check` (`binaries` + `desktop-entry` checks) |
| GUI runtime libs via `LD_LIBRARY_PATH` wrapper | `distro(nix)` | `permanent-downstream` | none | iced/wgpu dlopen Vulkan/Wayland/X11 at runtime; `cfait-gui` is `wrapProgram`-wrapped with those libs. The TUI binary is intentionally left unwrapped. | `nix flake check` (`desktop-entry`); manual `cfait-gui` launch under a Wayland/X11 session |
| `AWS_LC_SYS_NO_JITTER_ENTROPY=1` | `distro(nix)` | `permanent-downstream` | upstream Arch PKGBUILD sets the same | Keep while rustls pulls in `aws-lc-sys`; the jitter-entropy probe is non-deterministic in the Nix sandbox. Retire if cfait drops the aws_lc_rs backend. | package build succeeds reproducibly |
| HM module targets XDG `~/.config/cfait/config.toml` | `distro(nix)` | `permanent-downstream` | none | cfait uses `directories::ProjectDirs("com","cfait","cfait")` and has no config-dir env var. If upstream adds one, revisit the module's `home`/session-var handling. | `nix flake check` (`hm-module`) |

Statuses:

- `local-only`
- `temporary-shim`
- `planned-upstream-pr`
- `submitted-upstream-pr`
- `waiting-upstream-release`
- `permanent-downstream`
- `retire-candidate`
- `retired`
