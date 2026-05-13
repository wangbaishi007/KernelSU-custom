Place KernelSU module ZIPs in this folder (same structure as flashed modules).

Build path: manager/app/src/main/assets/bundled_modules/

Filename rules:
  - Only files ending in .zip are installed.
  - Order is alphabetical by filename.

When it runs:
  - Once per app data (see SharedPreferences bundled_modules_bootstrap).
  - Only if the KernelSU driver is active, kernel version is supported, and a root shell is available.
  - If root is not ready on first launch, the app retries on the next MainActivity open (until it succeeds or there are no zips).

After changing ZIPs in assets, clear app data or uninstall before testing "first install" again.
