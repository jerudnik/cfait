# Nix packaging for this fork

This directory was scaffolded from
[`4nix-utilities#fork-nix`](https://github.com/jerudnik/4nix-utilities). It packages
the upstream Rust project as a reproducible Nix flake and maintains the fork on a
three-rail branch model. See `docs/BRANCHING.md` for the full model.

## If you just ran `nix flake init -t ...#fork-nix`

Resolve the `@TOKEN@` placeholders automatically:

```sh
nix run github:jerudnik/4nix-utilities#fork-init
```

Then finish the two things it cannot infer:

1. **`nix/package.nix` -> `outputHashes`**: add a fixed-output hash for every
   `git+...#<rev>` dependency in `Cargo.lock`. List them and verify with:
   ```sh
   nix run github:jerudnik/4nix-utilities#fork-doctor
   ```
   Get a hash by building once and copying the value Nix reports as mismatched,
   or with `nix-prefetch-git`.
2. **Build stamp env vars**: confirm the `*_GIT_HASH` / `*_SEMVER` names in
   `nix/package.nix`'s `buildMeta` match what upstream's `build.rs` actually
   reads. If upstream has no build-time git stamp, set `buildMeta = { }`.

Create the Cachix cache and wire its token:

```sh
cachix create <name>            # the name in flake.nix's nixConfig
# add CACHIX_AUTH_TOKEN to the repo's GitHub Actions secrets
# paste the printed public key into flake.nix extra-trusted-public-keys
```

## Invariant: keep the build cache stable

The per-commit git stamp lives in a separate `buildMeta` attrset, applied only to
the final `craneLib.buildPackage`. It must NOT be in `commonArgs`, which feeds
`craneLib.buildDepsOnly`. If it leaks in, the entire dependency layer rebuilds on
every commit. `fork-doctor` enforces this; do not move it.

## Daily commands

```sh
nix build .#<pname>                                   # build the binary
nix run github:jerudnik/4nix-utilities#fork-status    # rail health
nix run github:jerudnik/4nix-utilities#fork-sync      # pull CI-reconciled rails
nix run github:jerudnik/4nix-utilities#fork-doctor    # validate model + packaging
```
