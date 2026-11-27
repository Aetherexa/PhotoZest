#!/usr/bin/env python3
"""Validate per-platform feature profiles and generate compile-time flags.

No dynamic service, API or photo upload is introduced. A profile is frozen into
each built artifact, so changing production flags requires a new deployment.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = {
    "android": ("onDeviceRecognition", "screenshotOcr", "originalFolderMoves"),
    "ios": ("onDeviceRecognition", "libraryScan", "albumOrganization"),
    "web": ("enhancedRecognition", "screenshotOcr", "smartCollections", "browserSharing", "folderImport"),
}
OUTPUT = {
    "android": ROOT / "android/app/src/main/java/com/aetherexa/photoflow/core/PhotoZestFeatureFlags.kt",
    "ios": ROOT / "ios/PhotoZest/PhotoZestFeatureFlags.generated.swift",
    "web": ROOT / "web/src/featureFlags.generated.ts",
}

def load(platform, profile):
    if platform not in SCHEMA or profile not in ("preview", "production"):
        raise ValueError(f"Unknown platform/profile: {platform}/{profile}")
    path = ROOT / f"config/features/{platform}.{profile}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    if set(data) != {"platform", "profile", "features"}:
        raise ValueError(f"{path}: unexpected or missing root fields")
    if data["platform"] != platform or data["profile"] != profile:
        raise ValueError(f"{path}: platform/profile mismatch")
    features = data["features"]
    if not isinstance(features, dict) or set(features) != set(SCHEMA[platform]):
        raise ValueError(f"{path}: flags must exactly match {list(SCHEMA[platform])}")
    if any(type(v) is not bool for v in features.values()):
        raise ValueError(f"{path}: flag values must be JSON booleans")
    return features

def render(platform, profile, features):
    header = f"// Generated from config/features/{platform}.{profile}.json by scripts/feature_flags.py.\n"
    if platform == "android":
        body = "package com.aetherexa.photoflow.core\n\nobject PhotoZestFeatureFlags {\n"
        body += "".join(f"    const val {key} = {str(features[key]).lower()}\n" for key in SCHEMA[platform])
        return header + body + "}\n"
    if platform == "ios":
        body = "enum PhotoZestFeatureFlags {\n"
        body += "".join(f"    static let {key} = {str(features[key]).lower()}\n" for key in SCHEMA[platform])
        return header + body + "}\n"
    body = "export const photoZestFeatures = Object.freeze({\n"
    body += "".join(f"  {key}: {str(features[key]).lower()},\n" for key in SCHEMA[platform])
    return header + body + "});\n"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--platform", choices=tuple(SCHEMA))
    parser.add_argument("--profile", choices=("preview", "production"), default="preview")
    parser.add_argument("--validate-all", action="store_true")
    parser.add_argument("--check", action="store_true")
    opts = parser.parse_args()
    for platform in SCHEMA:
        for profile in ("preview", "production"):
            load(platform, profile)
    print("Validated six independent feature profiles")
    if opts.validate_all:
        return
    if not opts.platform:
        parser.error("--platform required unless --validate-all")
    features = load(opts.platform, opts.profile)
    target = OUTPUT[opts.platform]
    content = render(opts.platform, opts.profile, features)
    if opts.check:
        if not target.exists() or target.read_text(encoding="utf-8") != content:
            raise SystemExit(f"{target} is out of sync with {opts.platform}/{opts.profile}")
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
    print(f"{opts.platform} {opts.profile}: " + ", ".join(f"{k}={v}" for k, v in features.items()))

if __name__ == "__main__":
    main()
