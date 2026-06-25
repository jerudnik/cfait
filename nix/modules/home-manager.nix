# Home Manager module for cfait.
#
# Exposed via `inputs.cfait.homeManagerModules.default`. Intentionally thin and
# unopinionated: it installs the package and (optionally) lets users declare the
# config file as freeform Nix attrs or an explicit path.
#
# cfait resolves its config via `directories::ProjectDirs("com","cfait","cfait")`,
# i.e. `$XDG_CONFIG_HOME/cfait/config.toml` (`~/.config/cfait/config.toml`).
# There is no config-dir environment variable; the only runtime override is the
# `--root DIR` CLI flag, which is per-invocation and therefore out of scope for a
# declarative module. So this module manages the XDG config file only.
{
  config,
  lib,
  pkgs,
  ...
}:
let
  cfg = config.programs.cfait;
  tomlFormat = pkgs.formats.toml { };
  managesConfig = cfg.configFile != null || cfg.settings != { };
in
{
  options.programs.cfait = {
    enable = lib.mkEnableOption "cfait";

    package = lib.mkOption {
      type = lib.types.package;
      default = pkgs.cfait;
      defaultText = lib.literalExpression "pkgs.cfait";
      description = ''
        The cfait package to install. Defaults to `pkgs.cfait`, which is
        provided when the flake's `overlays.default` is applied.
      '';
    };

    settings = lib.mkOption {
      inherit (tomlFormat) type;
      default = { };
      example = lib.literalExpression ''
        {
          offline_mode = false;
          log_level = "info";
        }
      '';
      description = ''
        Declarative contents of `~/.config/cfait/config.toml`, written as TOML.
        Left empty by default so cfait's own defaults apply. Mutually exclusive
        with `configFile`.

        Note: cfait rewrites this file at runtime (e.g. to migrate plaintext
        passwords into the Secret Service and bump `config_version`). Managing it
        declaratively makes it read-only, which is appropriate for fully-declared
        setups but will block those in-place migrations.
      '';
    };

    configFile = lib.mkOption {
      type = lib.types.nullOr lib.types.path;
      default = null;
      description = ''
        Path to a pre-authored `config.toml`. Takes precedence over `settings`.
      '';
    };
  };

  config = lib.mkIf cfg.enable {
    assertions = [
      {
        assertion = !(cfg.configFile != null && cfg.settings != { });
        message = "programs.cfait: set either `settings` or `configFile`, not both.";
      }
    ];

    home.packages = [ cfg.package ];

    # cfait reads ~/.config/cfait/config.toml (XDG). Use xdg.configFile so the
    # path tracks the user's XDG_CONFIG_HOME.
    xdg.configFile = lib.mkIf managesConfig {
      "cfait/config.toml" =
        if cfg.configFile != null then
          { source = cfg.configFile; }
        else
          { source = tomlFormat.generate "cfait-config.toml" cfg.settings; };
    };
  };
}
