package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;

import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

public abstract class Transaction implements Parcelable {
    private static final String LC = Util.getLogContext(Transaction.class);

    public static final long ID_NONE = -1;

    // Transactions
    //
    //
    // Playlist transactions
    //
    // PLAYLIST_DELETE(playlist_id)
    public static final String PLAYLIST_DELETE = "playlist_delete";
    // PLAYLIST_META_ADD(playlist_id, key, value)
    public static final String PLAYLIST_META_ADD = "playlist_meta_add";
    // PLAYLIST_META_DELETE(playlist_id, key, value)
    public static final String PLAYLIST_META_DELETE = "playlist_meta_delete";
    // PLAYLIST_META_DELETE(playlist_id, key) [currently used? needed?]
    public static final String PLAYLIST_META_DELETE_ALL = "playlist_meta_delete_all";
    // PLAYLIST_META_EDIT(playlist_id, key, oldValue, newValue) -> PLAYLIST_META_DELETE(playlist_id, key, oldValue) + PLAYLIST_META_ADD(playlist_id, key, newValue)
    public static final String PLAYLIST_META_EDIT = "playlist_meta_edit";
    //
    // PLAYLIST_ENTRY_ADD(playlist_id, beforePlaylistEntryID, beforePos, entryID, metaSnapshot) [entryID or PlaylistEntry as argument?]
    public static final String PLAYLIST_ENTRY_ADD = "playlist_entry_add";
    // PLAYLIST_ENTRY_DELETE(playlist_id, playlistEntryID, playlistEntryPositions)
    public static final String PLAYLIST_ENTRY_DELETE = "playlist_entry_delete";
    // PLAYLIST_ENTRY_MOVE(playlist_id, playlistEntryID, beforePlaylistEntryID, beforePos)
    public static final String PLAYLIST_ENTRY_MOVE = "playlist_entry_move";
    //
    //
    // Entry transactions
    //
    // META_ADD(entry_id, key, value)
    public static final String META_ADD = "meta_add";
    // META_DELETE(entry_id, key, value)
    public static final String META_DELETE = "meta_delete";
    // META_DELETE_ALL(entry_id, key)
    public static final String META_DELETE_ALL = "meta_delete_all";
    // META_EDIT(entry_id, key, oldValue, newValue) -> META_DELETE(key, oldValue) + META_ADD(key, newValue)
    public static final String META_EDIT = "meta_edit";

    private final long id;
    private final String src;
    private final Date date;
    private final long errorCount;
    private final String errorMessage;
    private final String type;

    public Transaction(long id,
                       String src,
                       Date date,
                       long errorCount,
                       String errorMessage,
                       String type
    ) {
        this.id = id;
        this.src = src;
        this.date = date;
        this.errorCount = errorCount;
        this.errorMessage = errorMessage;
        this.type = type;
    }

    public static Transaction from(long id,
                                   String src,
                                   Date date,
                                   long errorCount,
                                   String errorMessage,
                                   String action,
                                   String args
    ) {
        Transaction transaction = new TransactionUnknown(
                id, src, date, action, args, errorCount, errorMessage
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
                            id, src, date, errorCount, errorMessage, jsonArgs
                    );
                    break;
                case META_EDIT:
                    transaction = new TransactionMetaEdit(
                            id, src, date, errorCount, errorMessage, jsonArgs
                    );
                    break;
                case META_DELETE:
                    transaction = new TransactionMetaDelete(
                            id, src, date, errorCount, errorMessage, jsonArgs
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

    public String getType() {
        return type;
    }

    abstract JSONObject jsonArgs();

    public String getArgs() {
        return jsonArgs().toString();
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public int hashCode() {
        return Objects.hash(src, type);
    }

    public static final Creator<Transaction> CREATOR = new Creator<Transaction>() {
        @Override
        public Transaction createFromParcel(Parcel in) {
            long id = in.readLong();
            String src = in.readString();
            Date date = new Date(in.readLong());
            long errorCount = in.readLong();
            String errorMessage = in.readString();
            String type = in.readString();
            String args = in.readString();
            return Transaction.from(id, src, date, errorCount, errorMessage, type, args);
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
        parcel.writeString(getType());
        parcel.writeString(getArgs());
    }

    public abstract String getDisplayableAction();
    public abstract String getDisplayableDetails();

    public String apply(Context context) {
        if (!MusicLibraryService.checkAPISupport(getSrc(), getType())) {
            return "Transaction " + getType() + " not supported for " + getSrc();
        }
        return apply(context, MusicLibraryService.getAPIFromSource(getSrc()));
    }

    abstract String apply(Context context, String api);
}