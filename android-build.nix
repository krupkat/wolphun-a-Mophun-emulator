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
  # Gradle 5.6.4 / AGP 3.6.4 cannot run on current JDKs such as Java 21.
  jdk = pkgs.jdk11_headless;
  buildToolsVersion = "29.0.3";
  ndkVersion = "21.4.7075529";
  cmakeVersion = "3.10.2";
  projectDir = toString ./android;

  androidComposition = pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "29" ];
    buildToolsVersions = [ buildToolsVersion ];
    cmdLineToolsVersion = "8.0";
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
    # Use the checked-in wrapper, not an unrelated Gradle from nixpkgs.
    exec ${pkgs.bash}/bin/bash ${pkgs.lib.escapeShellArg "${projectDir}/gradlew"} \
      -p ${pkgs.lib.escapeShellArg projectDir} :app:assembleDebug "$@"
  '';
in
pkgs.mkShellNoCC {
  name = "wolphun-android-build";
  packages = [ jdk sdk pkgs.unzip build ];

  JAVA_HOME = jdk.home;
  ANDROID_HOME = sdkRoot;
  ANDROID_SDK_ROOT = sdkRoot; # Legacy AGP compatibility.
  ANDROID_NDK_ROOT = "${sdkRoot}/ndk/${ndkVersion}";
  # android/app/build.gradle accepts this override for the Nix-provided NDK.
  ORG_GRADLE_PROJECT_wolphunNdkVersion = ndkVersion;

  shellHook = ''
    # Maven's unpatched aapt2 executable cannot run directly on NixOS.
    export GRADLE_OPTS="''${GRADLE_OPTS:+$GRADLE_OPTS }-Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/${buildToolsVersion}/aapt2"

    # Android's CMake directory includes a build suffix (e.g. 3.10.2.4988404).
    cmake_root=( "$ANDROID_HOME"/cmake/${cmakeVersion}* )
    export PATH="''${cmake_root[0]}/bin:$PATH"

    # Regenerate the gitignored machine-local SDK paths, including any stale
    # paths left by Android Studio. This file is not part of the source tree.
    printf 'sdk.dir=%s\ncmake.dir=%s\n' "$ANDROID_HOME" "''${cmake_root[0]}" \
      > ${pkgs.lib.escapeShellArg "${projectDir}/local.properties"}
    unset cmake_root

    echo 'Run: wolphun-android-build'
    echo 'APK: android/app/build/outputs/apk/debug/app-debug.apk'
    echo 'First build downloads Gradle and Maven dependencies (network required).'
  '';
}
