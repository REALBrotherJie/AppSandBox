$ErrorActionPreference = 'Stop'
$env:ANDROID_SDK_ROOT = 'D:/Company/Install/Android/SDK'
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$dir = Join-Path $env:TEMP 'appsandbox-task15-avd'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$image = "$env:ANDROID_SDK_ROOT/system-images/android-36/google_apis/x86_64"
if (-not (Test-Path "$dir/encryptionkey.img")) {
    Copy-Item -LiteralPath "$image/encryptionkey.img" -Destination "$dir/encryptionkey.img"
}
# Use separate experiment disks; never wipe the user's original AVD.
Start-Process -FilePath "$env:ANDROID_SDK_ROOT/emulator/emulator.exe" -ArgumentList "-avd Pixel_3a_API_36_extension_level_19_x86_64 -sysdir $image -datadir $dir -data $dir/userdata.img -encryption-key $dir/encryptionkey.img -no-window -no-audio -no-snapshot -no-boot-anim -cores 2 -gpu swiftshader_indirect -feature -Vulkan" -WindowStyle Hidden
