package se.splushii.dancingbunnies.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceDialogFragmentCompat;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.backend.GitAPIClient.PROTOCOL_HTTPS;
import static se.splushii.dancingbunnies.backend.GitAPIClient.PROTOCOL_SSH;

public class GitRepoPreferenceDialogFragment extends PreferenceDialogFragmentCompat {
    private static final String LC = Util.getLogContext(GitRepoPreferenceDialogFragment.class);
    private static final String PREF_KEY_SUFFIX_PROTOCOL = "protocol";
    private static final String PREF_KEY_SUFFIX_USER = "user";
    private static final String PREF_KEY_SUFFIX_HOST = "host";
    private static final String PREF_KEY_SUFFIX_PATH = "path";
    private static final String PREF_KEY_SUFFIX_AUTH = "auth";
    private static final String PREF_KEY_SUFFIX_AUTH_PASSWORD = "auth_password";
    private static final String PREF_KEY_SUFFIX_AUTH_SSH_KEY = "auth_ssh_key";
    private static final String PREF_KEY_SUFFIX_AUTH_SSH_KEY_PASSPHRASE = "auth_ssh_key_passphrase";

    private static final String AUTH_PASSWORD = "password";
    private static final String AUTH_SSH_KEY = "ssh_key";

    private TextView URITextView;

    private String protocol;
    private String user;
    private String host;
    private String path;
    private String auth;
    private String password;
    private String sshKey;
    private String sshKeyPassphrase;

    public static DialogFragment newInstance(String key) {
        final GitRepoPreferenceDialogFragment fragment = new GitRepoPreferenceDialogFragment();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    public static String getInstanceID(SharedPreferences sp, String prefix) {
        return sp.getString(getKey(prefix, PREF_KEY_SUFFIX_HOST), "github.com")
                + "/"
                + sp.getString(getKey(prefix, PREF_KEY_SUFFIX_PATH), "my/repository.git");
    }

    public static Bundle getSettingsBundle(SharedPreferences sp, String prefix) {
        Bundle settings = new Bundle();
        settings.putString(
                APIClient.SETTINGS_KEY_GIT_REPO,
                getURI(sp, prefix)
        );
        settings.putString(
                APIClient.SETTINGS_KEY_GIT_USERNAME,
                sp.getString(getKey(prefix, PREF_KEY_SUFFIX_USER), "git")
        );
        String auth = sp.getString(getKey(prefix, PREF_KEY_SUFFIX_AUTH), AUTH_PASSWORD);
        switch (auth) {
            case AUTH_PASSWORD:
                settings.putString(
                        APIClient.SETTINGS_KEY_GIT_PASSWORD,
                        sp.getString(getKey(prefix, PREF_KEY_SUFFIX_AUTH_PASSWORD), "")
                );
                break;
            case AUTH_SSH_KEY:
                settings.putString(
                        APIClient.SETTINGS_KEY_GIT_SSH_KEY,
                        sp.getString(getKey(prefix, PREF_KEY_SUFFIX_AUTH_SSH_KEY), "")
                );
                settings.putString(
                        APIClient.SETTINGS_KEY_GIT_SSH_KEY_PASSPHRASE,
                        sp.getString(getKey(prefix, PREF_KEY_SUFFIX_AUTH_SSH_KEY_PASSPHRASE), "")
                );
                break;
        }
        return settings;
    }

    @Override
    protected View onCreateDialogView(Context context) {
        View rootView = View.inflate(context, R.layout.preference_dialog_git_repo_layout, null);
        RadioGroup protocolRadioGroup = rootView.findViewById(R.id.preference_dialog_git_repo_protocol);
        EditText userEditText = rootView.findViewById(R.id.preference_dialog_git_repo_user);
        EditText hostEditText = rootView.findViewById(R.id.preference_dialog_git_repo_host);
        EditText pathEditText = rootView.findViewById(R.id.preference_dialog_git_repo_path);
        URITextView = rootView.findViewById(R.id.preference_dialog_git_repo_uri);
        RadioGroup authRadioGroup = rootView.findViewById(R.id.preference_dialog_git_repo_auth_method);
        View authPasswordView = rootView.findViewById(R.id.preference_dialog_git_repo_auth_password_root);
        EditText authPasswordEditText = rootView.findViewById(R.id.preference_dialog_git_repo_auth_password);
        View authSSHKeyView = rootView.findViewById(R.id.preference_dialog_git_repo_auth_ssh_key_root);
        EditText authSSHKeyEditText = rootView.findViewById(R.id.preference_dialog_git_repo_auth_ssh_key);
        EditText authSSHKeyPassphraseEditText = rootView.findViewById(R.id.preference_dialog_git_repo_auth_ssh_key_passphrase);

        SharedPreferences sp = getPreference().getSharedPreferences();
        protocol = sp.getString(getKey(PREF_KEY_SUFFIX_PROTOCOL), PROTOCOL_HTTPS);
        user = sp.getString(getKey(PREF_KEY_SUFFIX_USER), "git");
        host = sp.getString(getKey(PREF_KEY_SUFFIX_HOST), "github.com");
        path = sp.getString(getKey(PREF_KEY_SUFFIX_PATH), "my/repository.git");
        auth = sp.getString(getKey(PREF_KEY_SUFFIX_AUTH), AUTH_PASSWORD);
        password = sp.getString(getKey(PREF_KEY_SUFFIX_AUTH_PASSWORD), "");
        sshKey = sp.getString(getKey(PREF_KEY_SUFFIX_AUTH_SSH_KEY), "");
        sshKeyPassphrase = sp.getString(getKey(PREF_KEY_SUFFIX_AUTH_SSH_KEY_PASSPHRASE), "");
        protocolRadioGroup.setOnCheckedChangeListener((radioGroup, checkedID) -> {
            switch (checkedID) {
                default:
                case R.id.preference_dialog_git_repo_protocol_https:
                    rootView.findViewById(R.id.preference_dialog_git_repo_auth_method_ssh_key).setEnabled(false);
                    if (authRadioGroup.getCheckedRadioButtonId() == R.id.preference_dialog_git_repo_auth_method_ssh_key) {
                        authRadioGroup.check(R.id.preference_dialog_git_repo_auth_method_password);
                    }
                    setProtocol(PROTOCOL_HTTPS);
                    break;
                case R.id.preference_dialog_git_repo_protocol_ssh:
                    rootView.findViewById(R.id.preference_dialog_git_repo_auth_method_ssh_key).setEnabled(true);
                    setProtocol(PROTOCOL_SSH);
                    break;
            }
            updateURI();
        });
        authRadioGroup.setOnCheckedChangeListener((radioGroup, checkedID) -> {
            switch (checkedID) {
                default:
                case R.id.preference_dialog_git_repo_auth_method_password:
                    authSSHKeyView.setVisibility(View.GONE);
                    authPasswordView.setVisibility(View.VISIBLE);
                    setAuth(AUTH_PASSWORD);
                    break;
                case R.id.preference_dialog_git_repo_auth_method_ssh_key:
                    authPasswordView.setVisibility(View.GONE);
                    authSSHKeyView.setVisibility(View.VISIBLE);
                    setAuth(AUTH_SSH_KEY);
                    break;
            }
        });
        switch (protocol) {
            default:
            case PROTOCOL_HTTPS:
                protocolRadioGroup.check(R.id.preference_dialog_git_repo_protocol_https);
                break;
            case PROTOCOL_SSH:
                protocolRadioGroup.check(R.id.preference_dialog_git_repo_protocol_ssh);
                break;
        }
        switch (auth) {
            default:
            case AUTH_PASSWORD:
                authRadioGroup.check(R.id.preference_dialog_git_repo_auth_method_password);
                break;
            case AUTH_SSH_KEY:
                authRadioGroup.check(R.id.preference_dialog_git_repo_auth_method_ssh_key);
                break;
        }
        userEditText.setText(user);
        userEditText.addTextChangedListener(new TextWatcher(PREF_KEY_SUFFIX_USER));
        hostEditText.setText(host);
        hostEditText.addTextChangedListener(new TextWatcher(PREF_KEY_SUFFIX_HOST));
        pathEditText.setText(path);
        pathEditText.addTextChangedListener(new TextWatcher(PREF_KEY_SUFFIX_PATH));
        updateURI();

        authPasswordEditText.setText(password);
        authPasswordEditText.addTextChangedListener(new TextWatcher(PREF_KEY_SUFFIX_AUTH_PASSWORD));
        authSSHKeyEditText.setText(sshKey);
        authSSHKeyEditText.addTextChangedListener(new TextWatcher(PREF_KEY_SUFFIX_AUTH_SSH_KEY));
        authSSHKeyPassphraseEditText.setText(sshKeyPassphrase);
        authSSHKeyPassphraseEditText.addTextChangedListener(new TextWatcher(PREF_KEY_SUFFIX_AUTH_SSH_KEY_PASSPHRASE));
        return rootView;
    }

    private class TextWatcher implements android.text.TextWatcher {
        final private String prefKeySuffix;

        TextWatcher(String prefKeySuffix) {
            this.prefKeySuffix = prefKeySuffix;
        }

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

        @Override
        public void afterTextChanged(Editable editable) {
            switch (prefKeySuffix) {
                case PREF_KEY_SUFFIX_USER:
                    setUser(editable.toString());
                    break;
                case PREF_KEY_SUFFIX_HOST:
                    setHost(editable.toString());
                    break;
                case PREF_KEY_SUFFIX_PATH:
                    setPath(editable.toString());
                    break;
                case PREF_KEY_SUFFIX_AUTH_PASSWORD:
                    setAuthPassword(editable.toString());
                    break;
                case PREF_KEY_SUFFIX_AUTH_SSH_KEY:
                    setAuthSSHKey(editable.toString());
                    break;
                case PREF_KEY_SUFFIX_AUTH_SSH_KEY_PASSPHRASE:
                    setAuthSSHKeyPassphrase(editable.toString());
                    break;
            }
            updateURI();
        }
    }

    private void setUser(String user) {
        getPreference().getSharedPreferences().edit()
                .putString(getKey(PREF_KEY_SUFFIX_USER), user)
                .apply();
        this.user = user;
    }

    private void setHost(String host) {
        getPreference().getSharedPreferences().edit()
                .putString(getKey(PREF_KEY_SUFFIX_HOST), host)
                .apply();
        this.host = host;
    }

    private void setPath(String path) {
        getPreference().getSharedPreferences().edit()
                .putString(getKey(PREF_KEY_SUFFIX_PATH), path)
                .apply();
        this.path = path;
    }

    private void setProtocol(String protocol) {
        getPreference().getSharedPreferences().edit()
                .putString(getKey(PREF_KEY_SUFFIX_PROTOCOL), protocol)
                .apply();
        this.protocol = protocol;
    }

    private void setAuth(String auth) {
        getPreference().getSharedPreferences().edit()
                .putString(getKey(PREF_KEY_SUFFIX_AUTH), auth)
                .apply();
        this.auth = auth;
    }

    private void setAuthPassword(String password) {
        getPreference().getSharedPreferences().edit()
                .putString(getKey(PREF_KEY_SUFFIX_AUTH_PASSWORD), password)
                .apply();
        this.password = password;
    }

    private void setAuthSSHKey(String sshKey) {
        getPreference().getSharedPreferences().edit()
                .putString(getKey(PREF_KEY_SUFFIX_AUTH_SSH_KEY), sshKey)
                .apply();
        this.sshKey = sshKey;
    }

    private void setAuthSSHKeyPassphrase(String sshKeyPassphrase) {
        getPreference().getSharedPreferences().edit()
                .putString(getKey(PREF_KEY_SUFFIX_AUTH_SSH_KEY_PASSPHRASE), sshKeyPassphrase)
                .apply();
        this.sshKeyPassphrase = sshKeyPassphrase;
    }

    private void updateURI() {
        URITextView.setText(getURI());
    }


    public static String getURI(SharedPreferences sp, String prefix) {
        return getURI(
                sp.getString(getKey(prefix, PREF_KEY_SUFFIX_PROTOCOL), PROTOCOL_HTTPS),
                sp.getString(getKey(prefix, PREF_KEY_SUFFIX_USER), "git"),
                sp.getString(getKey(prefix, PREF_KEY_SUFFIX_HOST), "github.com"),
                sp.getString(getKey(prefix, PREF_KEY_SUFFIX_PATH), "my/repository.git")
        );
    }

    private String getURI() {
        return getURI(protocol, user, host, path);
    }

    private static String getURI(String protocol, String user, String host, String path) {
        String URI = "";
        switch (protocol) {
            default:
            case PROTOCOL_HTTPS:
                URI += protocol + "://";
                break;
            case PROTOCOL_SSH:
                URI += user + "@";
                break;
        }
        URI += host;
        switch (protocol) {
            default:
            case PROTOCOL_HTTPS:
                URI += "/";
                break;
            case PROTOCOL_SSH:
                URI += ":";
                break;
        }
        URI += path;
        return URI;
    }

    private String getKey(String suffix) {
        String prefix = getPreference().getKey();
        return getKey(prefix, suffix);
    }

    private static String getKey(String prefix, String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return prefix;
        }
        return prefix + "_" + suffix;
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            GitRepoPreference gitRepoPref = (GitRepoPreference) getPreference();
            String uri = getURI();
            if (gitRepoPref.callChangeListener(uri)) {
                gitRepoPref.persistStringValue(uri);
            }
        }
    }
}
