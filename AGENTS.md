# UAPlay Android repository rules

- UAPlay-apk is standalone. Do not modify UAPlay-nx or other platform repositories for Android changes.
- Keep **one APK** supporting Phone / Tablet and Android TV / Google TV. Preserve first-launch mode selection and Settings mode switch.
- GitHub Actions may build a debug APK and commit the testing binary under apk/. This is not a GitHub Release.
- Publish a GitHub release only if the user explicitly requests one.
- Do not describe the alpha preview as a complete UAPlay catalog/player port until actually integrated.
