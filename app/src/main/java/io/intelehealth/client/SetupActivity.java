package io.intelehealth.client;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;

import io.intelehealth.client.objects.WebResponse;


public class SetupActivity extends AppCompatActivity {

    private final String LOG_TAG = "SetupActivity";

    private TestSetup mAuthTask = null;

    ProgressBar progressBar;
    private AutoCompleteTextView mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;
    protected AccountManager manager;
    private EditText mUrlField;
    private EditText mPrefixField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Persistent login information
        manager = AccountManager.get(SetupActivity.this);

        // Set up the login form.
        mEmailView = (AutoCompleteTextView) findViewById(R.id.email);
        // populateAutoComplete(); TODO: create our own autocomplete code

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mEmailSignInButton = (Button) findViewById(R.id.setup_submit_button);
        mEmailSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_TAG, "button pressed");
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);

        mUrlField = (EditText) findViewById(R.id.editText_URL);
        mPrefixField = (EditText) findViewById(R.id.editText_prefix);


        progressBar = (ProgressBar) findViewById(R.id.progressBar_setup);
        Button submitButton = (Button) findViewById(R.id.setup_submit_button);

        progressBar.setVisibility(View.GONE);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
//                progressBar.setVisibility(View.VISIBLE);
//                progressBar.setProgress(0);

            }
        });

        /*
        so have a textbox that allows to input a URL
        preset URL for the demo version of this app

        Then have them insert a prefix
        preset prefix can be JHU, and then they can test it

        maybe sure it is changed
        at least prefix MUST be changed before pressing submit

        once submit is clicked, do a progress bar that says checking

        if check works, say yes, and move on to home screen
        also save URL and Prefix to sharedprefs

        if check fails, then you say please check the URL
        thats if you couldnt even connect

        if you could connect, but the prefix returns ANYTHING then you need to do it again

        if you could connect, and prefix returns NOTHING, then you save it, and then home screen it



         */

        //TODO: add fields where they have to log in
        /* you can't set up a new prefix without having a login to test it against
        so they need to input URL, prefix, a UN, and a password
         */


    }


    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.


            String urlString = mUrlField.getText().toString();
            String prefixString = mPrefixField.getText().toString();
            mAuthTask = new TestSetup(urlString, prefixString, email, password);
            mAuthTask.execute();
            Log.d(LOG_TAG, "attempting setup");
        }
    }

    private boolean isEmailValid(String email) {
        //TODO: Replace this with your own logic
        return true;
    }

    private boolean isPasswordValid(String password) {
        //TODO: Replace this with your own logic
        return password.length() > 4;
    }


    private class TestSetup extends AsyncTask<Void, Void, Integer> {

        private final String USERNAME;
        private final String PASSWORD;
        private final String CLEAN_URL;
        private final String PREFIX;
        private String BASE_URL;

        TestSetup(String url, String prefix, String username, String password) {
            CLEAN_URL = url;
            PREFIX = prefix;
            USERNAME = username;
            PASSWORD = password;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }


        @Override
        protected Integer doInBackground(Void... params) {
            BufferedReader reader;
            String JSONString;

            WebResponse loginAttempt = new WebResponse();

            try {

                //TODO: grab the URL and the UN and PW from the sharedprefs, and the account

                Log.d(LOG_TAG, "UN: " + USERNAME);
                Log.d(LOG_TAG, "PW: " + PASSWORD);

                String urlModifier = "patient";
                String dataString = "?q=" + PREFIX;

                BASE_URL = "http://" + CLEAN_URL + ":8080/openmrs/ws/rest/v1/";
                String urlString = BASE_URL + urlModifier + dataString;

                URL url = new URL(urlString);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                String encoded = Base64.encodeToString((USERNAME + ":" + PASSWORD).getBytes("UTF-8"), Base64.NO_WRAP);
                connection.setRequestProperty("Authorization", "Basic " + encoded);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("USER-AGENT", "Mozilla/5.0");
                connection.setRequestProperty("ACCEPT-LANGUAGE", "en-US,en;0.5");

                int responseCode = connection.getResponseCode();
                loginAttempt.setResponseCode(responseCode);

                Log.d(LOG_TAG, "GET URL: " + url);
                Log.d(LOG_TAG, "Response Code from Server: " + String.valueOf(responseCode));

                // Read the input stream into a String
                InputStream inputStream = connection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Do Nothing.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }

                JSONString = buffer.toString();

                Log.d(LOG_TAG, "JSON Response: " + JSONString);
                loginAttempt.setResponseString(JSONString);
                if (loginAttempt != null && loginAttempt.getResponseCode() != 200) {
                    Log.d(LOG_TAG, "Login get request was unsuccessful");
                    return loginAttempt.getResponseCode();
                }

                if (!loginAttempt.getResponseString().isEmpty()) {
                    try {
                        JSONObject responseObject = new JSONObject(loginAttempt.getResponseString());
                        JSONArray results = responseObject.getJSONArray("results");
                        if (results.length() == 0) {
                            return 1;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return 201;
                    }
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
                return 3;
            } catch (IOException e) {
                e.printStackTrace();
                return 201;
            }

            return 201;
        }

        @Override
        protected void onPostExecute(Integer success) {
            mAuthTask = null;
//            showProgress(false);

            if (success == 1) {
                final Account account = new Account(USERNAME, "io.intelehealth.openmrs");
                manager.addAccountExplicitly(account, PASSWORD, null);

                SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(SettingsActivity.KEY_PREF_SERVER_URL, BASE_URL);
                editor.putBoolean(SettingsActivity.KEY_PREF_SETUP_COMPLETE, true);
                editor.putString(SettingsActivity.KEY_PREF_ID_PREFIX, PREFIX);
                editor.commit();



                Intent intent = new Intent(SetupActivity.this, HomeActivity.class);
                startActivity(intent);
                finish();
            } else if (success == 201) {
                mPasswordView.setError(getString(R.string.error_incorrect_password));
                mPasswordView.requestFocus();
            } else if (success == 3) {
                mUrlField.setError("Check your URL.");
                mUrlField.requestFocus();
            } else {
                mPrefixField.setError("Select a different prefix!");
                mPrefixField.requestFocus();
            }
        }
    }
}