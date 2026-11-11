# nix-shell android-emulator.nix
# Then: wolphun-android-emulator (build the APK with android-build.nix first).
# Uses Google's Android SDK/system image under their licenses (accepted below).
# Requires x86_64 Linux with KVM access; on NixOS add your user to the kvm group.
# If NIX_PATH is missing, add: -I nixpkgs=flake:nixpkgs
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
  # A runtime filename, NOT a Nix path: the APK need not exist at evaluation
  # time, is not copied into the store, and can be rebuilt in another shell.
  apk ? "${toString ./.}/android/app/build/outputs/apk/debug/app-debug.apk",
}:
let
  platformTools = pkgs.androidenv.androidPkgs.platform-tools;
  package = "com.manedsoftware.wolphun";
  activity = "${package}.LibraryActivity";

  # emulateApp currently hardcodes command-line tools 8.0. Override its SDK
  # composer so avdmanager also uses modern tooling for the API 36 image.
  emulateApp = pkgs.androidenv.emulateApp.override {
    composeAndroidPackages = args: pkgs.androidenv.composeAndroidPackages (args // {
      cmdLineToolsVersion = "19.0";
    });
  };
  emulator = emulateApp {
    name = "wolphun-android-emulator";
    platformVersion = "36";
    abiVersion = "x86_64"; # Also built by the app's native-library abiFilters.
    systemImageType = "default"; # No Google Play services needed.
    deviceName = "wolphun-api36";
    app = apk;
    inherit package activity;

    # Persist installed games and emulator state between invocations.
    # emulateApp expands $HOME in its generated launch script.
    androidUserHome = "$HOME/.local/share/wolphun-android";
    configOptions = {
      "hw.keyboard" = "yes";
      "hw.ramSize" = "2048";
      "hw.gpu.enabled" = "yes";
      "hw.gpu.mode" = "auto";
    };
    sdkExtraArgs = {
      toolsVersion = null;
      buildToolsVersions = [ "36.0.0" ];
      includeCmake = false;
      includeNDK = false;
    };
  };

  launch = pkgs.writeShellApplication {
    name = "wolphun-android-emulator";
    runtimeInputs = [ pkgs.coreutils pkgs.gnugrep ];
    text = ''
      if [[ ! -f ${pkgs.lib.escapeShellArg apk} ]]; then
        echo 'Debug APK not found. Run wolphun-android-build in android-build.nix first.' >&2
        exit 1
      fi
      exec ${emulator}/bin/run-test-emulator
    '';
  };

  install = pkgs.writeShellApplication {
    name = "wolphun-android-install";
    runtimeInputs = [ platformTools ];
    text = ''
      # emulateApp only installs when the package is absent. Use this command
      # after rebuilding to update an already-running emulator, keeping saves.
      adb install -r ${pkgs.lib.escapeShellArg apk}
      adb shell am force-stop ${package}
      adb shell am start -n ${package}/${activity}
    '';
  };
in
assert pkgs.lib.assertMsg (pkgs.stdenv.hostPlatform.system == "x86_64-linux")
  "This shell targets x86_64 Linux with KVM.";
pkgs.mkShellNoCC {
  name = "wolphun-android-emulator";
  packages = [ launch install platformTools ];

  shellHook = ''
    if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
      echo 'Warning: /dev/kvm is not accessible. Enable KVM and check kvm group membership.' >&2
    fi
    echo 'Run: wolphun-android-emulator'
    echo 'After rebuilding: wolphun-android-install'
    echo 'ROM import: adb push game.mpn /sdcard/Download/; then tap + in the app and select it.'
    echo 'Emulator flags: export NIX_ANDROID_EMULATOR_FLAGS="-gpu software" (or other emulator options).'
  '';
}
