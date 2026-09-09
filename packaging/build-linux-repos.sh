#!/usr/bin/env bash
# Builds signed APT and DNF repositories from the CLI's .deb and .rpm packages.
#
# Both are static file trees, so the output is published as-is to GitHub Pages.
# Existing packages in <site> are kept and reindexed, so older versions stay
# installable after a new release.
set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo "Usage: $0 <site-dir> <gpg-key-id> <artifacts-dir>" >&2
    exit 2
fi

site=$1
key_id=$2
artifacts=$3

# The signing key is passphrase protected. Loopback pinentry only tells gpg not
# to open a tty; without the passphrase it still fails with "Inappropriate ioctl
# for device". rpm needs it as a file because its sign command is a template we
# cannot pipe into, so both paths share one.
pass_file=""
if [[ -n "${PACKAGE_SIGNING_PASSPHRASE:-}" ]]; then
    pass_file=$(mktemp)
    chmod 600 "$pass_file"
    printf '%s' "$PACKAGE_SIGNING_PASSPHRASE" > "$pass_file"
fi
trap '[[ -n "$pass_file" ]] && rm -f "$pass_file"' EXIT

gpg_args=(--batch --yes --pinentry-mode loopback --local-user "$key_id")
rpm_gpg_args=(--batch --pinentry-mode loopback --yes)
if [[ -n "$pass_file" ]]; then
    gpg_args+=(--passphrase-file "$pass_file")
    rpm_gpg_args+=(--passphrase-file "$pass_file")
fi

base_url="https://vinnovateit.github.io/latch"
origin="Latch"
suite="stable"
component="main"
architecture="amd64"

shopt -s nullglob
debs=("$artifacts"/*.deb)
rpms=("$artifacts"/*.rpm)
shopt -u nullglob

if [[ ${#debs[@]} -eq 0 && ${#rpms[@]} -eq 0 ]]; then
    echo "No .deb or .rpm found in $artifacts" >&2
    exit 2
fi

# ---------------------------------------------------------------- APT --------
pool="$site/apt/pool/$component/l/latch-cli"
index_dir="$site/apt/dists/$suite/$component/binary-$architecture"
mkdir -p "$pool" "$index_dir"

for deb in "${debs[@]}"; do
    cp -f "$deb" "$pool/"
done

# Paths inside Packages must be relative to the apt root, so scan from there.
(
    cd "$site/apt"
    # --multiversion indexes every version in the pool, not just the newest, so
    # a user can pin or downgrade if a release turns out to be broken.
    dpkg-scanpackages --multiversion --arch "$architecture" pool \
        > "dists/$suite/$component/binary-$architecture/Packages"
    gzip -9cn "dists/$suite/$component/binary-$architecture/Packages" \
        > "dists/$suite/$component/binary-$architecture/Packages.gz"

    apt-ftparchive \
        -o "APT::FTPArchive::Release::Origin=$origin" \
        -o "APT::FTPArchive::Release::Label=$origin" \
        -o "APT::FTPArchive::Release::Suite=$suite" \
        -o "APT::FTPArchive::Release::Codename=$suite" \
        -o "APT::FTPArchive::Release::Architectures=$architecture" \
        -o "APT::FTPArchive::Release::Components=$component" \
        -o "APT::FTPArchive::Release::Description=Latch CLI packages" \
        release "dists/$suite" > "dists/$suite/Release"

    # InRelease is the inline-signed form apt prefers; Release.gpg is the
    # detached signature older clients look for. Publish both.
    rm -f "dists/$suite/InRelease" "dists/$suite/Release.gpg"
    gpg "${gpg_args[@]}" --clearsign -o "dists/$suite/InRelease" "dists/$suite/Release"
    gpg "${gpg_args[@]}" --armor --detach-sign -o "dists/$suite/Release.gpg" "dists/$suite/Release"
)

# ---------------------------------------------------------------- DNF --------
if [[ ${#rpms[@]} -gt 0 ]]; then
    mkdir -p "$site/rpm"
    for rpm_file in "${rpms[@]}"; do
        cp -f "$rpm_file" "$site/rpm/"
    done

    # Sign the packages themselves, not just the metadata, so dnf verifies at
    # both levels with gpgcheck and repo_gpgcheck on.
    #
    # __gpg has to be set explicitly: rpm defaults it to a gpg2 binary that does
    # not exist on Debian-family systems, and the failure is a bare "Could not
    # exec gpg". The loopback pinentry keeps it from reaching for a tty.
    rpm --define "_gpg_name $key_id" \
        --define "__gpg $(command -v gpg)" \
        --define "_gpg_sign_cmd_extra_args ${rpm_gpg_args[*]}" \
        --addsign "$site"/rpm/*.rpm

    createrepo_c --update "$site/rpm"
    rm -f "$site/rpm/repodata/repomd.xml.asc"
    gpg "${gpg_args[@]}" --armor --detach-sign "$site/rpm/repodata/repomd.xml"
fi

# ------------------------------------------------------------- key + docs ----
gpg --export "$key_id" > "$site/latch.gpg"
gpg --armor --export "$key_id" > "$site/latch.gpg.asc"

cat > "$site/latch.repo" <<EOF
[latch]
name=Latch
baseurl=$base_url/rpm
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=$base_url/latch.gpg.asc
EOF

cat > "$site/index.html" <<EOF
<!doctype html>
<meta charset="utf-8">
<title>Latch package repositories</title>
<style>
body{font:16px/1.6 system-ui,sans-serif;max-width:46rem;margin:3rem auto;padding:0 1rem}
pre{background:#f4f4f5;padding:1rem;overflow-x:auto;border-radius:6px}
code{font:14px/1.5 ui-monospace,monospace}
</style>
<h1>Latch package repositories</h1>
<p>APT and DNF repositories for <a href="https://github.com/vinnovateit/latch">Latch CLI</a>.</p>

<h2>Debian and Ubuntu</h2>
<pre><code>curl -fsSL $base_url/latch.gpg | sudo tee /usr/share/keyrings/latch.gpg > /dev/null
echo "deb [signed-by=/usr/share/keyrings/latch.gpg] $base_url/apt $suite $component" | sudo tee /etc/apt/sources.list.d/latch.list
sudo apt update
sudo apt install latch-cli</code></pre>

<h2>Fedora and RPM-based</h2>
<pre><code>sudo dnf config-manager --add-repo $base_url/latch.repo
sudo dnf install latch-cli</code></pre>

<p>Signing key fingerprint: <code>$key_id</code></p>
EOF

# GitHub Pages runs Jekyll by default, which would skip the repodata directory
# and anything else it considers special.
touch "$site/.nojekyll"

echo "Built repositories in $site"
