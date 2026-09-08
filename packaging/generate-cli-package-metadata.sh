#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
    echo "Usage: $0 <version> <linux-tar.gz> <windows.zip> <output-dir>" >&2
    exit 2
fi

version=$1
linux_archive=$2
windows_archive=$3
output_dir=$4

if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+([-.][0-9A-Za-z.-]+)?$ ]]; then
    echo "Invalid version: $version" >&2
    exit 2
fi

expected_linux="latch-cli-${version}-linux-x64.tar.gz"
expected_windows="latch-cli-${version}-windows-x64.zip"

if [[ $(basename "$linux_archive") != "$expected_linux" ]]; then
    echo "Expected Linux artifact named $expected_linux" >&2
    exit 2
fi
if [[ $(basename "$windows_archive") != "$expected_windows" ]]; then
    echo "Expected Windows artifact named $expected_windows" >&2
    exit 2
fi
if [[ ! -f $linux_archive || ! -f $windows_archive ]]; then
    echo "Both release artifacts must exist" >&2
    exit 2
fi

linux_sha=$(sha256sum "$linux_archive" | awk '{print $1}')
windows_sha=$(sha256sum "$windows_archive" | awk '{print toupper($1)}')
aur_dir="$output_dir/aur"
winget_dir="$output_dir/winget/VinnovateIT.LatchCLI/$version"
choco_dir="$output_dir/chocolatey"
mkdir -p "$aur_dir" "$winget_dir" "$choco_dir/tools"

cat > "$aur_dir/PKGBUILD" <<EOF
# Maintainer: VinnovateIT
pkgname=latch-cli-bin
pkgver=$version
pkgrel=1
pkgdesc='Automatic VIT hostel Wi-Fi login from the terminal'
arch=('x86_64')
url='https://github.com/vinnovateit/latch'
license=('MIT')
depends=('networkmanager')
optdepends=('libsecret: Secret Service credential storage'
            'rfkill: Wi-Fi radio fallback'
            'wireless_tools: SSID detection fallback')
provides=('latch-cli')
conflicts=('latch-cli')
options=('!strip')
source=("latch-cli-\${pkgver}-linux-x64.tar.gz::https://github.com/vinnovateit/latch/releases/download/v\${pkgver}/latch-cli-\${pkgver}-linux-x64.tar.gz")
sha256sums=('$linux_sha')

package() {
    install -d "\$pkgdir/opt/latch-cli" "\$pkgdir/usr/bin"
    cp -a "\$srcdir/latch-cli-\${pkgver}-linux-x64/." "\$pkgdir/opt/latch-cli/"
    ln -s /opt/latch-cli/bin/latch-cli "\$pkgdir/usr/bin/latch-cli"
}
EOF

cat > "$aur_dir/.SRCINFO" <<EOF
pkgbase = latch-cli-bin
	pkgdesc = Automatic VIT hostel Wi-Fi login from the terminal
	pkgver = $version
	pkgrel = 1
	url = https://github.com/vinnovateit/latch
	arch = x86_64
	license = MIT
	depends = networkmanager
	optdepends = libsecret: Secret Service credential storage
	optdepends = rfkill: Wi-Fi radio fallback
	optdepends = wireless_tools: SSID detection fallback
	provides = latch-cli
	conflicts = latch-cli
	options = !strip
	source = latch-cli-$version-linux-x64.tar.gz::https://github.com/vinnovateit/latch/releases/download/v$version/latch-cli-$version-linux-x64.tar.gz
	sha256sums = $linux_sha

pkgname = latch-cli-bin
EOF

cat > "$winget_dir/VinnovateIT.LatchCLI.yaml" <<EOF
# yaml-language-server: \$schema=https://aka.ms/winget-manifest.version.1.10.0.schema.json
PackageIdentifier: VinnovateIT.LatchCLI
PackageVersion: $version
DefaultLocale: en-US
ManifestType: version
ManifestVersion: 1.10.0
EOF

cat > "$winget_dir/VinnovateIT.LatchCLI.installer.yaml" <<EOF
# yaml-language-server: \$schema=https://aka.ms/winget-manifest.installer.1.10.0.schema.json
PackageIdentifier: VinnovateIT.LatchCLI
PackageVersion: $version
InstallerType: zip
NestedInstallerType: portable
Commands:
  - latch-cli
UpgradeBehavior: uninstallPrevious
Installers:
  - Architecture: x64
    NestedInstallerFiles:
      - RelativeFilePath: latch-cli-$version-windows-x64\\latch-cli.exe
        PortableCommandAlias: latch-cli
    InstallerUrl: https://github.com/vinnovateit/latch/releases/download/v$version/latch-cli-$version-windows-x64.zip
    InstallerSha256: $windows_sha
ManifestType: installer
ManifestVersion: 1.10.0
EOF

cat > "$winget_dir/VinnovateIT.LatchCLI.locale.en-US.yaml" <<EOF
# yaml-language-server: \$schema=https://aka.ms/winget-manifest.defaultLocale.1.10.0.schema.json
PackageIdentifier: VinnovateIT.LatchCLI
PackageVersion: $version
PackageLocale: en-US
Publisher: VinnovateIT
PublisherUrl: https://vinnovateit.com
PublisherSupportUrl: https://github.com/vinnovateit/latch/issues
PackageName: Latch CLI
PackageUrl: https://github.com/vinnovateit/latch
License: MIT
ShortDescription: Automatic VIT hostel Wi-Fi login from the terminal
Description: Latch CLI detects VIT hostel Wi-Fi networks, stores credentials securely, logs in automatically, and can share one active engine with the Latch Desktop app.
Tags:
  - cli
  - network
  - vit
  - wifi
ManifestType: defaultLocale
ManifestVersion: 1.10.0
EOF

# Chocolatey downloads the same Windows ZIP the winget manifest points at, and
# verifies it against the same checksum. Nothing is embedded in the .nupkg, so
# the package stays small and there is one artifact to trust.
cat > "$choco_dir/latch-cli.nuspec" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://schemas.microsoft.com/packaging/2015/06/nuspec.xsd">
  <metadata>
    <id>latch-cli</id>
    <version>$version</version>
    <packageSourceUrl>https://github.com/vinnovateit/latch/tree/main/packaging</packageSourceUrl>
    <owners>VinnovateIT</owners>
    <title>Latch CLI</title>
    <authors>VinnovateIT</authors>
    <projectUrl>https://github.com/vinnovateit/latch</projectUrl>
    <projectSourceUrl>https://github.com/vinnovateit/latch</projectSourceUrl>
    <bugTrackerUrl>https://github.com/vinnovateit/latch/issues</bugTrackerUrl>
    <licenseUrl>https://github.com/vinnovateit/latch/blob/main/LICENSE</licenseUrl>
    <requireLicenseAcceptance>false</requireLicenseAcceptance>
    <tags>latch cli wifi vit network captive-portal</tags>
    <summary>Automatic VIT hostel Wi-Fi login from the terminal</summary>
    <description>Latch CLI detects VIT hostel Wi-Fi networks, stores credentials securely, logs in automatically, and can share one active engine with the Latch Desktop app. The package bundles its own trimmed Java runtime, so Java does not need to be installed separately.</description>
    <releaseNotes>https://github.com/vinnovateit/latch/releases/tag/v$version</releaseNotes>
  </metadata>
  <files>
    <file src="tools\\**" target="tools" />
  </files>
</package>
EOF

cat > "$choco_dir/tools/chocolateyInstall.ps1" <<EOF
\$ErrorActionPreference = 'Stop'

\$toolsDir = Split-Path -Parent \$MyInvocation.MyCommand.Definition

# Unzipping into the tools directory is what gets latch-cli.exe shimmed onto
# PATH; Chocolatey shims every executable it finds there.
Install-ChocolateyZipPackage \`
  -PackageName 'latch-cli' \`
  -Url 'https://github.com/vinnovateit/latch/releases/download/v$version/latch-cli-$version-windows-x64.zip' \`
  -UnzipLocation \$toolsDir \`
  -Checksum '$windows_sha' \`
  -ChecksumType 'sha256'
EOF

cat > "$choco_dir/tools/chocolateyUninstall.ps1" <<'EOF'
$ErrorActionPreference = 'Stop'

# Install-ChocolateyZipPackage records what it extracted, so removing the
# package directory is enough; the shim goes with it.
Uninstall-ChocolateyZipPackage -PackageName 'latch-cli' -ZipFileName 'latch-cli-windows-x64.zip'
EOF

# Chocolatey moderation requires this for any package that fetches a binary.
cat > "$choco_dir/tools/VERIFICATION.txt" <<EOF
VERIFICATION

This package downloads the official Latch CLI archive published by VinnovateIT:

  https://github.com/vinnovateit/latch/releases/download/v$version/latch-cli-$version-windows-x64.zip

Its SHA256 checksum is pinned in tools/chocolateyInstall.ps1 as:

  $windows_sha

To verify, download the archive from the URL above and compare:

  Get-FileHash latch-cli-$version-windows-x64.zip -Algorithm SHA256

The archive is built from tag v$version by the release workflow in
https://github.com/vinnovateit/latch/blob/main/.github/workflows/release.yml
EOF

echo "Generated AUR, winget and chocolatey metadata for v$version in $output_dir"
