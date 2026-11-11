# nix-shell android-build.nix
# Then: wolphun-android-build
# Uses Google's Android SDK/NDK under their licenses (accepted below).
# If NIX_PATH is missing, use: nix-shell -I nixpkgs=flake:nixpkgs android-build.nix
{
  pkgs ?
    let
      channel = builtins.tryEval <nixpkgs>;
      source = if channel.success then channel.value else (builtins.getFlake "nixpkgs").outPath;
    in
    import source {
      config.allowUnfree = true;
      config.android_sdk.accept_license = true;
    },
}:
let
  # Gradle 8.14 / AGP 8.13 support Java 21 (AGP requires at least Java 17).
  jdk = pkgs.jdk21_headless;
  # Use the CLI without the setup hooks intended for sandboxed Gradle derivations.
  gradle = (pkgs.gradle_8.override { java = jdk; }).unwrapped;
  buildToolsVersion = "36.0.0";
  ndkVersion = "28.2.13676358";
  cmakeVersion = "3.22.1";
  projectDir = toString ./android;

  androidComposition = pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "36" ];
    buildToolsVersions = [ buildToolsVersion ];
    cmdLineToolsVersion = "19.0";
    toolsVersion = null;
    includeNDK = true;
    ndkVersions = [ ndkVersion ];
    includeCmake = true;
    cmakeVersions = [ cmakeVersion ];
    includeEmulator = false;
    includeSystemImages = false;
  };
  sdk = androidComposition.androidsdk;
  sdkRoot = "${sdk}/libexec/android-sdk";

  build = pkgs.writeShellScriptBin "wolphun-android-build" ''
    # Gradle itself is provided by Nix; do not invoke/download through gradlew.
    exec ${gradle}/bin/gradle \
      -p ${pkgs.lib.escapeShellArg projectDir} :app:assembleDebug "$@"
  '';
in
assert pkgs.lib.assertMsg
  (pkgs.lib.versionAtLeast gradle.version "8.13" && pkgs.lib.versionOlder gradle.version "9")
  "AGP 8.13 requires Gradle 8.13+ in the 8.x series; use a newer Nixpkgs.";
pkgs.mkShellNoCC {
  name = "wolphun-android-build";
  packages = [ jdk gradle sdk pkgs.unzip build ];

  JAVA_HOME = jdk.home;
  ANDROID_HOME = sdkRoot;
  ANDROID_SDK_ROOT = sdkRoot; # Compatibility with other Android tooling.
  ANDROID_NDK_ROOT = "${sdkRoot}/ndk/${ndkVersion}";
  # android/app/build.gradle accepts this override for the Nix-provided NDK.
  ORG_GRADLE_PROJECT_wolphunNdkVersion = ndkVersion;

  shellHook = ''
    # Maven's unpatched aapt2 executable cannot run directly on NixOS.
    # AGP labels this override experimental and warns about it; retain it so
    # resource compilation uses Nix's patched executable. Do not suppress warnings.
    export GRADLE_OPTS="''${GRADLE_OPTS:+$GRADLE_OPTS }-Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/${buildToolsVersion}/aapt2"

    # Some Android CMake packages append a build number to their directory.
    cmake_root=( "$ANDROID_HOME"/cmake/${cmakeVersion}* )
    export PATH="''${cmake_root[0]}/bin:$PATH"

    # Regenerate the gitignored machine-local SDK paths, including any stale
    # paths left by Android Studio. This machine-local file is gitignored.
    printf 'sdk.dir=%s\ncmake.dir=%s\n' "$ANDROID_HOME" "''${cmake_root[0]}" \
      > ${pkgs.lib.escapeShellArg "${projectDir}/local.properties"}
    unset cmake_root

    echo 'Run: wolphun-android-build'
    echo 'APK: android/app/build/outputs/apk/debug/app-debug.apk'
    echo 'Gradle is provided by Nix (no Gradle wrapper download).'
    echo 'Uncached Maven dependencies still require network access.'
  '';
}
