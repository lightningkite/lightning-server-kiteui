#!/usr/bin/env bash
# Installs or upgrades the KiteUI Kotlin Toolchain plugin shim in this project.
#
#   ./kiteui-setup.sh <codegen-version> --module <module-path>[,<module-path>...] --package <package>
#
# The shim is a handful of generated files that delegate to com.lightningkite.kiteui:codegen.
# All real code generation lives in that published jar, so upgrading is just re-running this
# script with a newer version. Safe to re-run: it rewrites the generated files and leaves
# everything else in project.yaml / module.yaml alone.
set -euo pipefail

GROUP_PATH="com/lightningkite/kiteui"
ARTIFACT="codegen"
REPO="${KITEUI_REPO:-https://repo1.maven.org/maven2}"
CACHE="${KITEUI_CACHE:-$HOME/.kiteui/codegen}"
INTO="kiteui-plugin"
MODULE=""
PACKAGE=""
MAVEN_REPO=""
ONLY=""
APPLY_TEMPLATE=""
IOS_PROJECT=""

usage() {
  sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
}

[ $# -ge 1 ] || usage
VERSION="$1"; shift
while [ $# -gt 0 ]; do
  case "$1" in
    --module)  MODULE="$2"; shift 2 ;;
    --package) PACKAGE="$2"; shift 2 ;;
    --into)    INTO="$2"; shift 2 ;;
    --repo)    REPO="$2"; shift 2 ;;
    --maven-repo) MAVEN_REPO="$2"; shift 2 ;;
    --only)    ONLY="$2"; shift 2 ;;   # comma list: resources,strings,routes
    --apply-template) APPLY_TEMPLATE="$2"; shift 2 ;;
    --ios-project) IOS_PROJECT="$2"; shift 2 ;;   # Xcode dir holding Info.plist + Assets.xcassets
    *) echo "Unknown option: $1" >&2; usage ;;
  esac
done
[ -n "$MODULE" ] || { echo "--module is required" >&2; usage; }
[ -n "$PACKAGE" ] || { echo "--package is required" >&2; usage; }
[ -f project.yaml ] || { echo "Run this from the project root (no project.yaml here)" >&2; exit 1; }

# Report the upgrade before doing it, so a re-run is obviously a no-op rather than a mystery.
PREVIOUS=""
if [ -f "$INTO/module.yaml" ]; then
  PREVIOUS="$(sed -n "s|^  - com\.lightningkite\.kiteui:$ARTIFACT:\(.*\)$|\1|p" "$INTO/module.yaml" | head -1)"
fi
if [ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$VERSION" ]; then
  echo "Upgrading kiteui codegen $PREVIOUS -> $VERSION"
elif [ -n "$PREVIOUS" ]; then
  echo "Reinstalling kiteui codegen $VERSION"
else
  echo "Installing kiteui codegen $VERSION"
fi

# Prefer a locally published build (useful when developing kiteui itself), else fetch and cache.
JAR="$HOME/.m2/repository/$GROUP_PATH/$ARTIFACT/$VERSION/$ARTIFACT-$VERSION.jar"
if [ -f "$JAR" ] && [ -z "$MAVEN_REPO" ] && [ -z "$APPLY_TEMPLATE" ]; then
  # Resolved from the local Maven repo, so the build has to look there too.
  MAVEN_REPO="mavenLocal"
  echo "Found $ARTIFACT $VERSION in ~/.m2; pointing the shim module at mavenLocal"
fi
if [ ! -f "$JAR" ]; then
  JAR="$CACHE/$ARTIFACT-$VERSION.jar"
  if [ ! -f "$JAR" ]; then
    URL="$REPO/$GROUP_PATH/$ARTIFACT/$VERSION/$ARTIFACT-$VERSION.jar"
    echo "Fetching $URL"
    mkdir -p "$CACHE"
    curl -fsSL "$URL" -o "$JAR.part"
    mv "$JAR.part" "$JAR"
  fi
fi
echo "Using $JAR"

# Init needs only the JDK, so this works before the project has resolved any dependency.
java -cp "$JAR" com.lightningkite.kiteui.codegen.Init init \
  --version "$VERSION" --project . --into "$INTO" --module "$MODULE" --package "$PACKAGE" \
  ${MAVEN_REPO:+--maven-repo "$MAVEN_REPO"} \
  ${ONLY:+--only "$ONLY"} \
  ${APPLY_TEMPLATE:+--apply-template "$APPLY_TEMPLATE"} \
  ${IOS_PROJECT:+--ios-project "$IOS_PROJECT"}

echo "Done. Run 'kotlin build' to generate sources and compile."
