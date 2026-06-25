{
  description = "Powerful, fast and elegant task / TODO manager (GUI & TUI, CalDAV & local)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

    flake-parts.url = "github:hercules-ci/flake-parts";

    crane.url = "github:ipetkov/crane";

    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };

    home-manager = {
      url = "github:nix-community/home-manager";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  # Public, safe-to-share binary cache for prebuilt outputs.
  #
  # Create the cache with `cachix create jerudnik-cfait` and put the auth token
  # in the repo's `CACHIX_AUTH_TOKEN` secret, then paste the printed public key
  # below. Until then these lines are inert.
  nixConfig = {
    extra-substituters = [ "https://jerudnik-cfait.cachix.org" ];
    extra-trusted-public-keys = [
      "jerudnik-cfait.cachix.org-1:REPLACE_WITH_PUBLIC_KEY_FROM_cachix_create"
    ];
  };

  outputs =
    inputs@{ flake-parts, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      # cfait's GUI (iced/wgpu) and Secret Service integration are Linux-only.
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];

      flake = {
        # Overlay: consumers add `inputs.cfait.overlays.default` and get
        # `pkgs.cfait`.
        overlays.default = final: prev: {
          cfait = inputs.self.packages.${final.stdenv.hostPlatform.system}.cfait;
        };

        # Home Manager module. Use as
        #   imports = [ inputs.cfait.homeManagerModules.default ];
        #   programs.cfait.enable = true;
        homeManagerModules.default = import ./nix/modules/home-manager.nix;
        homeModules.default = import ./nix/modules/home-manager.nix; # HM >= 24.11 alias
      };

      perSystem =
        {
          self',
          system,
          ...
        }:
        let
          pkgs = import inputs.nixpkgs {
            inherit system;
            overlays = [ (import inputs.rust-overlay) ];
          };
          inherit (pkgs) lib;

          rustToolchain = pkgs.rust-bin.stable.latest.default.override {
            extensions = [
              "rust-src"
              "clippy"
              "rustfmt"
            ];
          };

          craneLib = (inputs.crane.mkLib pkgs).overrideToolchain rustToolchain;

          version = (craneLib.crateNameFromCargoToml { src = ./.; }).version;

          cfait = pkgs.callPackage ./nix/package.nix {
            inherit craneLib version;
          };

          # The iced/wgpu GUI dlopen()s these at runtime; mirror the package's
          # wrapper set so the dev shell can run cfait-gui too.
          guiRuntimeLibs = [
            pkgs.fontconfig
            pkgs.freetype
            pkgs.libxkbcommon
            pkgs.libsecret
            pkgs.libx11
            pkgs.libxcb
            pkgs.libxcursor
            pkgs.libxi
            pkgs.libxrandr
            pkgs.vulkan-loader
            pkgs.wayland
          ];
        in
        {
          _module.args.pkgs = pkgs;

          packages = {
            default = cfait;
            inherit cfait;
          };

          # CI gates run by `nix flake check`: verify the GUI desktop integration
          # and that the Home Manager module evaluates. We do NOT duplicate
          # upstream clippy/rustfmt/test here (owned by upstream CI against its
          # pinned toolchain).
          checks = {
            # Both binaries must be present and the GUI one must be wrapped.
            binaries = pkgs.runCommandLocal "cfait-binaries-check" { } ''
              for bin in cfait cfait-gui; do
                test -x ${cfait}/bin/$bin || {
                  echo "missing binary: $bin" >&2
                  exit 1
                }
              done
              touch $out
            '';

            desktop-entry = pkgs.runCommandLocal "cfait-desktop-entry-check" { } ''
              desktop=${cfait}/share/applications/cfait.desktop
              test -f "$desktop" || {
                echo "missing desktop entry: $desktop" >&2
                exit 1
              }

              for needle in \
                'Name=Cfait' \
                'Exec=${cfait}/bin/cfait-gui %F' \
                'Icon=cfait' \
                'StartupWMClass=cfait-gui' \
                'Categories=Office;Utility;'; do
                grep -qxF "$needle" "$desktop" || {
                  echo "desktop entry missing expected line: $needle" >&2
                  cat "$desktop" >&2
                  exit 1
                }
              done

              icon=${cfait}/share/icons/hicolor/scalable/apps/cfait.svg
              test -f "$icon" || {
                echo "missing icon: $icon" >&2
                exit 1
              }

              touch $out
            '';

            hm-module =
              (inputs.home-manager.lib.homeManagerConfiguration {
                pkgs = import inputs.nixpkgs { inherit system; };
                modules = [
                  inputs.self.homeManagerModules.default
                  {
                    home.username = "cfait-check";
                    home.homeDirectory = "/tmp/cfait-check";
                    home.stateVersion = "24.11";
                    programs.cfait = {
                      enable = true;
                      package = cfait;
                      settings = {
                        offline_mode = true;
                        log_level = "info";
                      };
                    };
                  }
                ];
              }).activationPackage;
          };

          devShells.default = craneLib.devShell {
            checks = self'.checks;
            packages = [
              pkgs.cargo-nextest
              pkgs.cargo-audit
              pkgs.cargo-watch
              pkgs.nixfmt-rfc-style
              pkgs.pkg-config
              pkgs.cmake
              pkgs.perl
            ];

            buildInputs = guiRuntimeLibs ++ [ pkgs.stdenv.cc.cc.lib ];

            AWS_LC_SYS_NO_JITTER_ENTROPY = "1";
            LD_LIBRARY_PATH = lib.makeLibraryPath guiRuntimeLibs;
            shellHook = ''
              echo "cfait dev shell — rust $(rustc --version 2>/dev/null || echo '?')"
            '';
          };

          formatter = pkgs.nixfmt-rfc-style;
        };
    };
}
