# Enter the shell: nix-shell desktop.nix
# Launch the GUI:  wolphun [path/to/game.mpn]
# Or directly:     nix-shell desktop.nix --run wolphun
# If nix-shell warns about missing channels, add: -I nixpkgs=flake:nixpkgs
# Requires an X11 display (XWayland on Wayland) and your own .mpn ROMs.
{
  pkgs ?
    let
      channel = builtins.tryEval <nixpkgs>;
      # Flake-only NixOS installations may not have a nixpkgs channel in NIX_PATH.
      source = if channel.success then channel.value else (builtins.getFlake "nixpkgs").outPath;
    in
    import source { },
}:
let
  # Do not use jdk21_headless: the front-end needs Swing/AWT and Java Sound.
  jdk = pkgs.jdk21;
  # A real JRE, not pkgs.jre (which aliases the full JDK). java.desktop
  # includes Swing/AWT, image codecs and Java Sound; dependencies are resolved
  # by jlink. Keep legacy character encodings available for game text too.
  jre = pkgs.jre21_minimal.override {
    modules = [ "java.base" "java.desktop" "jdk.charsets" ];
  };

  wolphun = pkgs.stdenvNoCC.mkDerivation {
    pname = "wolphun-desktop";
    version = "0.3.0";

    # Copy only source and GUI resources, not local ROMs, saves or build outputs.
    src = pkgs.lib.fileset.toSource {
      root = ./desktop;
      fileset = pkgs.lib.fileset.unions [
        ./desktop/src
        ./desktop/assets/wolphun.png
        ./desktop/assets/kofi-qr.png
      ];
    };

    nativeBuildInputs = [ jdk pkgs.makeWrapper ];
    dontConfigure = true;

    # Equivalent to desktop/build.bat; no Gradle, Maven or downloaded JARs.
    buildPhase = ''
      runHook preBuild
      mkdir -p out
      find src -name '*.java' -print | sort > sources.txt
      javac --release 8 -encoding UTF-8 -d out @sources.txt
      cp assets/wolphun.png assets/kofi-qr.png out/
      jar cfe wolphun.jar mophun.Emulator -C out .
      runHook postBuild
    '';

    installPhase = ''
      runHook preInstall
      install -Dm644 wolphun.jar "$out/share/wolphun/wolphun.jar"
      makeWrapper ${jre}/bin/java "$out/bin/wolphun" \
        --add-flags "-Xmx768m -jar $out/share/wolphun/wolphun.jar" \
        --prefix PATH : ${pkgs.lib.makeBinPath [ pkgs.xdg-utils ]}
      runHook postInstall
    '';
  };
in
pkgs.mkShellNoCC {
  name = "wolphun-desktop";
  packages = [ wolphun ];

  shellHook = ''
    # Avoid blank Swing windows with non-reparenting/tiling window managers.
    export _JAVA_AWT_WM_NONREPARENTING="''${_JAVA_AWT_WM_NONREPARENTING:-1}"
    echo 'Run: wolphun [path/to/game.mpn]'
    echo 'Settings, saves and screenshots: ~/.mophunemu/'
    echo 'After editing Java sources, re-enter this shell to rebuild.'
  '';
}
