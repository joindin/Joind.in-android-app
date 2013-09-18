# OAuth2 configuration

If you're building the Joind.In Android app, you'll need to set up the OAuth2 configuration.

First, you'll need an API key from the Joind.In website. When you set this up, you'll need to
supply a callback URL. This can be anything you want, but it must be a valid URL.  Make a note
of this, as you'll need it in the next step. An example might be:

**http://yourjoindin.dev/oauth-callback**

Note that the URL doesn't have to actually exist as a valid page - the app will intercept the
request and bring you back out of the OAuth process.

Next, you'll need to create a file called ``oauth.properties`` and place it in ``res/raw``.
The content should be similar to the following:

    api_key=ABCDE12345
    callback=http://your.callback/url

except inserting your API key and callback URL as appropriate.

Once you've placed this in, authentication via the app's settings page should work.
