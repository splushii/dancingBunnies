package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;

import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.util.Util;

public abstract class Transaction implements Parcelable {
    private static final String LC = Util.getLogContext(Transaction.class);

    public static final long ID_NONE = -1;

    // Transaction groups
    public static final String GROUP_LIBRARY = "library";
    public static final String GROUP_PLAYLISTS = "playlists";

    // Library group actions
    public static final String META_ADD = "meta_add";
    public static final String META_DELETE = "meta_delete";
    public static final String META_EDIT = "meta_edit";

    // Playlist group actions
    public static final String PLAYLIST_ADD = "playlist_add";
    public static final String PLAYLIST_DELETE = "playlist_delete";
    public static final String PLAYLIST_META_ADD = "playlist_meta_add";
    public static final String PLAYLIST_META_DELETE = "playlist_meta_delete";
    public static final String PLAYLIST_META_EDIT = "playlist_meta_edit";
    public static final String PLAYLIST_ENTRY_ADD = "playlist_entry_add";
    public static final String PLAYLIST_ENTRY_DELETE = "playlist_entry_delete";
    public static final String PLAYLIST_ENTRY_MOVE = "playlist_entry_move";

    private final long id;
    private final String src;
    private final Date date;
    private final long errorCount;
    private final String errorMessage;
    private final String group;
    private final String action;
    private final boolean appliedLocally;

    public Transaction(long id,
                       String src,
                       Date date,
                       long errorCount,
                       String errorMessage,
                       boolean appliedLocally,
                       String group,
                       String action
    ) {
        this.id = id;
        this.src = src;
        this.date = date;
        this.errorCount = errorCount;
        this.errorMessage = errorMessage;
        this.group = group;
        this.action = action;
        this.appliedLocally = appliedLocally;
    }

    public static Transaction from(long id,
                                   String src,
                                   Date date,
                                   long errorCount,
                                   String errorMessage,
                                   boolean appliedLocally,
                                   String group,
                                   String action,
                                   String args
    ) {
        Transaction transaction = new TransactionUnknown(
                id, src, date, group, action, args, errorCount, errorMessage, appliedLocally
        );
        JSONObject jsonArgs;
        try {
             jsonArgs = new JSONObject(args);
        } catch (JSONException e) {
            e.printStackTrace();
            return transaction;
        }
        try {
            switch (action) {
                case META_ADD:
                    transaction = new TransactionMetaAdd(
                            id, src, date, errorCount, errorMessage, appliedLocally, jsonArgs
                    );
                    break;
                case META_EDIT:
                    transaction = new TransactionMetaEdit(
                            id, src, date, errorCount, errorMessage, appliedLocally, jsonArgs
                    );
                    break;
                case META_DELETE:
                    transaction = new TransactionMetaDelete(
                            id, src, date, errorCount, errorMessage, appliedLocally, jsonArgs
                    );
                    break;
                case PLAYLIST_ADD:
                    transaction = new TransactionPlaylistAdd(
                            id, src, date, errorCount, errorMessage, appliedLocally, jsonArgs
                    );
                    break;
                case PLAYLIST_DELETE:
                    transaction = new TransactionPlaylistDelete(
                            id, src, date, errorCount, errorMessage, appliedLocally, jsonArgs
                    );
                    break;
                case PLAYLIST_ENTRY_ADD:
                    transaction = new TransactionPlaylistEntryAdd(
                            id, src, date, errorCount, errorMessage, appliedLocally, jsonArgs
                    );
                    break;
                case PLAYLIST_ENTRY_DELETE:
                    transaction = new TransactionPlaylistEntryDelete(
                            id, src, date, errorCount, errorMessage, appliedLocally, jsonArgs
                    );
                    break;
                case PLAYLIST_ENTRY_MOVE:
                    transaction = new TransactionPlaylistEntryMove(
                            id, src, date, errorCount, errorMessage, appliedLocally, jsonArgs
                    );
                    break;
                case PLAYLIST_META_ADD:
                    transaction = new TransactionPlaylistMetaAdd(
                            id, src, date, errorCount, errorMessage, appliedLocally, jsonArgs
                    );
                    break;
                case PLAYLIST_META_DELETE:
                    transaction = new TransactionPlaylistMetaDelete(
                            id, src, date, errorCount, errorMessage, appliedLocally, jsonArgs
                    );
                    break;
                case PLAYLIST_META_EDIT:
                    transaction = new TransactionPlaylistMetaEdit(
                            id, src, date, errorCount, errorMessage, appliedLocally, jsonArgs
                    );
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return transaction;
    }

    public long getID() {
        return id;
    }

    public String getSrc() {
        return src;
    }

    public Date getDate() {
        return date;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getGroup() {
        return group;
    }

    public String getAction() {
        return action;
    }

    abstract JSONObject jsonArgs();

    public String getArgs() {
        return jsonArgs().toString();
    }

    public abstract String getArgsSource();

    public boolean isAppliedLocally() {
        return appliedLocally;
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public int hashCode() {
        return Objects.hash(src, action);
    }

    public static final Creator<Transaction> CREATOR = new Creator<Transaction>() {
        @Override
        public Transaction createFromParcel(Parcel in) {
            long id = in.readLong();
            String src = in.readString();
            Date date = new Date(in.readLong());
            long errorCount = in.readLong();
            String errorMessage = in.readString();
            boolean appliedLocally = in.readInt() == 1;
            String group = in.readString();
            String action = in.readString();
            String args = in.readString();
            return Transaction.from(id, src, date, errorCount, errorMessage, appliedLocally, group, action, args);
        }

        @Override
        public Transaction[] newArray(int size) {
            return new Transaction[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(getID());
        parcel.writeString(getSrc());
        parcel.writeLong(date.getTime());
        parcel.writeLong(getErrorCount());
        parcel.writeString(getErrorMessage());
        parcel.writeInt(appliedLocally ? 1 : 0);
        parcel.writeString(getGroup());
        parcel.writeString(getAction());
        parcel.writeString(getArgs());
    }

    public abstract String getDisplayableAction();
    public abstract String getDisplayableDetails();

    // Optimistically apply changes locally
//    public abstract CompletableFuture<Void> applyLocally(Context context);

    // Batch changes to be applied to the actual backend
    // Returns true iff transaction could be added to current transaction batch
    public abstract boolean addToBatch(Context context, APIClient.Batch batch)
            throws APIClient.BatchException;
}