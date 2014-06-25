# OAuth2 configuration

If you're building the joind.in Android app, you'll need to set up the OAuth2 configuration.

It's best if you first set up the joind.in API locally - that way you can have full control over
the client configuration.

The Android app uses the OAuth2 "Client Credentials Flow" method of authentication - this means that
your client ID needs to be authorised by the API.

## Configuring the API
In the API project, open up `src/config.php` and add an entry to the `password_client_ids` file:

    $config =  array(
        'mode' => 'development',
        'oauth' => array(
            'password_client_ids' => array(
                // ...
                'androidapp',
            )
        ),
    );

This will allow credentials access with a client ID of "androidapp".

## Configuring the Android app
In the Android app project, you'll need to create a file called ``oauth.properties`` and place it
in ``res/raw``.  The content should be:

    client_id=androidapp

Once you've placed this in, authentication via the app's settings page should work when you build
the app and point it to your local API.
