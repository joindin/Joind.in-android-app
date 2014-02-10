# Joind.in Android app

This is the Joind.in application for Android smartphones.

The latest version can be found on GitHub in the Joind.In organisation:

  http://github.com/joindin/Joind.in-android-app.git

Please report any issues at the Joind.In JIRA project, in the Android component:

  https://joindin.jira.com/browse/JOINDIN/component/10210

## Building the application

Please read the documentation on how to [configure OAuth2](/oauth_configuration.md) before you build.

If you're building from within an IDE, you'll need the Crashlytics plugin installed - grab the relevant one from the [Crashlytics site](https://www.crashlytics.com/downloads/plugins).

### From the command line

The application can be built with Ant or Gradle.

With Ant:

    cd source
    ant debug
  
or `ant release` if required.
  
With Gradle:

    gradle build

To install the application on connected device(s):

    cd source
    adb install -r bin/JoindIn-debug.apk
    
replacing `debug` with `release` if you built the release version.

### From an IDE

Both Eclipse and Android Studio are able to build the application.

## History

For more info about the history of the application, please visit:

  http://www.adayinthelifeof.nl/2010/10/09/joind-in-android-app-v1-6/

See LICENSE file for license information for this software
