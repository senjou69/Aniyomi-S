Looking to report an issue/bug or make a feature request? Please refer to the [README file](https://github.com/tachiyomiorg/tachiyomi#issues-feature-requests-and-contributing).

---

Thanks for your interest in contributing to Tachiyomi!


# Code contributions

Pull requests are welcome!

If you're interested in taking on [an open issue](https://github.com/tachiyomiorg/tachiyomi/issues), please comment on it so others are aware.


# Translations

Translations are done externally via Weblate. See [our website](https://aniyomi.jmir.xyz/help/contribution/#translation) for more details.


# Forks

Forks are allowed so long as they abide by [the project's LICENSE](https://github.com/tachiyomiorg/tachiyomi/blob/master/LICENSE).

When creating a fork, remember to:

- To avoid confusion with the main app:
    - Change the app name
    - Change the app icon
    - Change or disable the [app update checker](https://github.com/tachiyomiorg/tachiyomi/blob/master/app/src/main/java/eu/kanade/tachiyomi/data/updater/GithubUpdateChecker.kt)
- To avoid installation conflicts:
    - Change the `applicationId` in [`build.gradle.kts`](https://github.com/tachiyomiorg/tachiyomi/blob/master/app/build.gradle.kts)