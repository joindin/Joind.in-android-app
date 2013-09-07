# OAuth2 configuration

If you're building the Joind.In Android app, you'll need to set up the OAuth2 configuration.

First, you'll need an API key from the Joind.In website. When you set this up, you'll need to
supply a callback URL. Use the following:

**joindin://oauth-response/**

Copy the file ``oauth.dist`` from ``res/raw/oauth.dist`` to ``res/values/oauth.xml`` and open
it up.  You'll see where your API key should go.

Once you've placed this in, authentication via the app's settings page should work.
