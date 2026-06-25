# Crane-based package definition for `cfait` (CalDAV TODO manager).
#
# cfait ships two Linux binaries from one package:
#   * cfait     - the TUI / non-interactive CLI (default feature `tui`)
#   * cfait-gui - the iced (wgpu) desktop GUI    (feature `gui`)
#
# Both are built together with `--features gui`. The GUI binary loads Vulkan,
# Wayland and X11 libraries at runtime via dlopen, so those are wrapped onto it
# via LD_LIBRARY_PATH at install time (the TUI binary needs none of them).
#
# Structural note for the fork template: cfait has no build-time git stamp
# (version comes straight from CARGO_PKG_VERSION) and no cargo-git dependencies,
# so `buildMeta` is empty and the crane dependency layer is naturally
# cache-stable. `fork-doctor` verifies the stamp invariant.
{
  lib,
  stdenv,
  craneLib,
  # Native build tools
  pkg-config,
  cmake,
  perl,
  makeWrapper,
  # Native / runtime inputs
  fontconfig,
  freetype,
  libxkbcommon,
  libsecret,
  libx11,
  libxcb,
  libxcursor,
  libxi,
  libxrandr,
  vulkan-loader,
  wayland,
  version,
}:
let
  # The iced/wgpu GUI dlopen()s these at runtime; they must be on
  # LD_LIBRARY_PATH for cfait-gui. The TUI binary does not use them.
  guiRuntimeLibs = [
    fontconfig
    freetype
    libxkbcommon
    libsecret
    libx11
    libxcb
    libxcursor
    libxi
    libxrandr
    vulkan-loader
    wayland
  ];

  # Only the workspace sources cargo needs, plus the assets the postInstall step
  # consumes (desktop entry + scalable icon). Keeping this tight avoids rebuilds
  # when docs/CI/nix files change.
  src = lib.fileset.toSource {
    root = ../.;
    fileset = lib.fileset.unions [
      ../Cargo.toml
      ../Cargo.lock
      (lib.fileset.maybeMissing ../rust-toolchain.toml)
      (lib.fileset.maybeMissing ../.cargo)
      ../build.rs
      ../src
      ../assets
      ../locales
      (lib.fileset.maybeMissing ../tools)
      (lib.fileset.maybeMissing ../tests)
      (lib.fileset.maybeMissing ../uniffi.toml)
    ];
  };

  commonArgs = {
    inherit src version;
    pname = "cfait";
    strictDeps = true;

    cargoLock = "${src}/Cargo.lock";
    # cfait's Cargo.lock has no git dependencies, so no outputHashes are needed.

    # Build the GUI binary as well as the TUI. Without this the `gui` feature
    # (and the cfait-gui binary) is never compiled.
    cargoExtraArgs = "--locked --features gui --bin cfait --bin cfait-gui";

    nativeBuildInputs = [
      pkg-config
      cmake
      perl # required by aws-lc-sys (rustls aws_lc_rs backend)
      makeWrapper
    ];

    buildInputs = [
      fontconfig
      freetype
      libxkbcommon
      libsecret
      stdenv.cc.cc.lib
    ]
    ++ guiRuntimeLibs;

    # aws-lc-sys is pulled in transitively (rustls). Disable its jitter-entropy
    # probe so the build is deterministic in the Nix sandbox; upstream sets the
    # same flag in its Arch PKGBUILD.
    AWS_LC_SYS_NO_JITTER_ENTROPY = "1";

    CARGO_PROFILE = "release";
  };

  # Build all workspace dependencies once; reused for the package build. No
  # per-commit build metadata flows in here, so this layer is cache-stable.
  cargoArtifacts = craneLib.buildDepsOnly commonArgs;
in
craneLib.buildPackage (
  commonArgs
  // {
    inherit cargoArtifacts;

    # cfait's test suite reaches the network / a live CalDAV server and a Secret
    # Service, neither of which exist in the Nix sandbox. Tests run in upstream
    # CI; the package build stays focused on compilation.
    doCheck = false;

    # cfait-gui needs its Vulkan/Wayland/X11 libs on LD_LIBRARY_PATH. Install the
    # desktop entry (pointed at the wrapped binary) and the scalable hicolor icon
    # so the GUI is launchable from a desktop environment. The TUI binary is left
    # unwrapped.
    postInstall = ''
      wrapProgram "$out/bin/cfait-gui" \
        --prefix LD_LIBRARY_PATH : ${lib.makeLibraryPath guiRuntimeLibs}

      install -Dm0644 assets/cfait.desktop \
        "$out/share/applications/cfait.desktop"
      substituteInPlace "$out/share/applications/cfait.desktop" \
        --replace-fail 'Exec=cfait-gui %F' "Exec=$out/bin/cfait-gui %F"

      install -Dm0644 assets/cfait.svg \
        "$out/share/icons/hicolor/scalable/apps/cfait.svg"
    '';

    meta = {
      description = "Powerful, fast and elegant task / TODO manager (GUI & TUI, CalDAV & local)";
      homepage = "https://github.com/jerudnik/cfait";
      license = lib.licenses.gpl3Only;
      mainProgram = "cfait";
      platforms = lib.platforms.linux;
    };
  }
)
