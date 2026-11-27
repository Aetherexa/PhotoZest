#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
src=".build-sources/photoflow"
target="android"
mkdir -p "$target"
cat "$src"/part-*.b64 | base64 --decode | tar -xJ -C "$target"
cat "$src"/v02-*.b64 | base64 --decode | tar -xJ -C "$target"
for version in v03 v04 v05 v06 v07; do
  base64 --decode "$src/${version}-overlay.b64" | tar -xJ -C "$target"
done
cp "$src/v08-app-build.gradle.kts" "$target/app/build.gradle.kts"
cp "$src/v08-proguard-rules.pro" "$target/app/proguard-rules.pro"
mkdir -p "$target/app/src/main/java/com/aetherexa/photoflow/core"
mkdir -p "$target/app/src/test/java/com/aetherexa/photoflow/core"
cp "$src/v09-PlanPolicy.kt" "$target/app/src/main/java/com/aetherexa/photoflow/core/PlanPolicy.kt"
cp "$src/v09-PlanPolicyTest.kt" "$target/app/src/test/java/com/aetherexa/photoflow/core/PlanPolicyTest.kt"
# Retain the Kotlin namespace to avoid breaking internal imports and Android manifest resolution.
# Only the installable appId and visible label are renamed.
sed -i 's/com\.aetherexa\.photoflow\.compact/com.aetherexa.photozest/g' "$target/app/build.gradle.kts"
sed -i 's/com\.aetherexa\.photoflow\.recognition/com.aetherexa.photozest/g' "$target/app/build.gradle.kts"
sed -i 's/PhotoFlow/PhotoZest/g' "$target/app/src/main/res/values/strings.xml"
# Store code must remain gated; policy definitions are not verified purchases.
# Android UI includes some legacy hard-coded PhotoFlow product strings.
# Change only user-facing text; preserve internal Kotlin class/package identifiers.
main="$target/app/src/main/java/com/aetherexa/photoflow/MainActivity.kt"
vm="$target/app/src/main/java/com/aetherexa/photoflow/PhotoFlowViewModel.kt"
sed -i 's/Text("PhotoFlow"/Text("PhotoZest"/g' "$main"
sed -i 's/PhotoFlow organizes/PhotoZest organizes/g' "$main"
sed -i 's/PhotoFlow cannot/PhotoZest cannot/g' "$main"
sed -i 's/PhotoFlow needs/PhotoZest needs/g' "$main"
sed -i 's/PhotoFlow selected photos/PhotoZest selected photos/g' "$main"
sed -i 's/deleted by PhotoFlow/deleted by PhotoZest/g' "$vm"
sed -i 's/PhotoFlowAndroid/PhotoZestAndroid/g' "$target/settings.gradle.kts"
echo "PhotoZest Android sources restored to $target"
