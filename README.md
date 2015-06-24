# Joind.in Android app

This is the joind.in application for Android smartphones.

The latest version can be found on GitHub in the joind.in organisation:

  http://github.com/joindin/Joind.in-android-app.git

Please report any issues at the joind.in JIRA project, in the Android component:

  https://joindin.jira.com/browse/JOINDIN/component/10210

## Building the application

The application should be set up as any other Android project.
Please read the documentation on how to [configure OAuth2](/oauth_configuration.md) before you build.

Building requires that you have the following items installed:

* Gradle
* Android SDK (at least SDK version 22)

To build with Gradle, run:

    gradle build

in the project root. The output APK is produced in `build/outputs/apk`.

## Installation

To install the application on a connected device:

    adb install -r build/output/apk/<name of output APK>.apk

## Crashlytics

The joind.in app uses the Crashlytics library to handle crash reporting. You shouldn't
need to do anything out of the ordinary, as Gradle will handle pulling in the Crashlytics
dependency. There are Crashlytics plugins for all the major IDEs however, but they don't
add anything extra to the build process so not essential to have installed.

## History

For more info about the history of the application, please visit:

  http://www.adayinthelifeof.nl/2010/10/09/joind-in-android-app-v1-6/

See LICENSE file for license information for this software
