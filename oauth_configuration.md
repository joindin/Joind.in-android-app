# OAuth2 configuration

If you're building the Joind.In Android app, you'll need to set up the OAuth2 configuration.

First, you'll need an API key from the Joind.In website. When you set this up, you'll need to
supply a callback URL. Use the following:

**joindin://oauth-response/**

Next, you'll need to create a file called ``oauth.properties`` and place it in ``res/raw``.
The content should be similar to the following (a single line):

    api_key=ABCDE12345

except inserting your API key as appropriate.

Once you've placed this in, authentication via the app's settings page should work.
