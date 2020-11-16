package se.splushii.dancingbunnies.ui;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.util.Util;

public class ConfirmationDialogFragment extends DialogFragment {
    private static final String LC = Util.getLogContext(ConfirmationDialogFragment.class);

    private static final String TAG = "dancingbunnies.splushii.se.fragment_tag.confirm_dialog";
    private static final String BUNDLE_KEY_ID = "dancingbunnies.bundle.key.confirm_dialog.id";
    private static final String BUNDLE_KEY_TITLE = "dancingbunnies.bundle.key.confirm_dialog.title";
    private static final String BUNDLE_KEY_MESSAGE = "dancingbunnies.bundle.key.confirm_dialog.message";

    private String id;
    private String title;
    private String message;

    public static void showDialog(Fragment fragment, String id, String title, String message) {
        Bundle args = new Bundle();
        args.putString(BUNDLE_KEY_ID, id);
        args.putString(BUNDLE_KEY_TITLE, title);
        args.putString(BUNDLE_KEY_MESSAGE, message);
        Util.showDialog(
                fragment,
                TAG,
                MainActivity.REQUEST_CODE_CONFIRMATION_DIALOG,
                new ConfirmationDialogFragment(),
                args
        );
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            return;
        }
        id = args.getString(BUNDLE_KEY_ID);
        title = args.getString(BUNDLE_KEY_TITLE);
        message = args.getString(BUNDLE_KEY_MESSAGE);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, id) ->
                        onClicked(ConfirmationDialogFragment.this.id, true)
                )
                .setNegativeButton(R.string.cancel, (dialog, id) ->
                        onClicked(ConfirmationDialogFragment.this.id, false)
                );
        return builder.create();
    }

    private void onClicked(String id, boolean confirmed) {
        Fragment fragment = getTargetFragment();
        if (fragment instanceof Handler) {
            ((Handler) fragment).onConfirmationDialogClicked(id, confirmed);
        }
    }

    public interface Handler {
        void onConfirmationDialogClicked(String id, boolean confirmed);
    }
}
