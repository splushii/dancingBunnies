package se.splushii.dancingbunnies.storage.db;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = DB.TABLE_LIBRARY_TRANSACTIONS,
        indices = {
                @Index(value = { DB.COLUMN_SRC })
        }
)
public class Transaction {
    static final String COLUMN_ROW_ID = DB.COLUMN_ROW_ID;
    static final String COLUMN_DATE = "date";
    static final String COLUMN_SRC = "src";
    static final String COLUMN_GROUP = "grp";
    private static final String COLUMN_ACTION = "action";
    private static final String COLUMN_ARGS = "args";
    static final String COLUMN_ERROR = "err";
    static final String COLUMN_ERROR_NUM = "errnum";
    static final String COLUMN_APPLIED_LOCALLY = "locally";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = DB.COLUMN_ROW_ID)
    public long rowid;
    @ColumnInfo(name = COLUMN_DATE)
    public Date date;
    @NonNull
    @ColumnInfo(name = COLUMN_SRC)
    public String src;
    @NonNull
    @ColumnInfo(name = COLUMN_GROUP)
    public String group;
    @NonNull
    @ColumnInfo(name = COLUMN_ACTION)
    public String action;
    @NonNull
    @ColumnInfo(name = COLUMN_ARGS)
    public String args;
    @ColumnInfo(name = COLUMN_ERROR)
    public String error;
    @NonNull
    @ColumnInfo(name = COLUMN_ERROR_NUM, defaultValue = "0")
    public long numErrors;
    @NonNull
    @ColumnInfo(name = COLUMN_APPLIED_LOCALLY, defaultValue = "0")
    public boolean appliedLocally;

    public static Transaction from(
            String src,
            Date date,
            String group,
            String action,
            String args
    ) {
        Transaction transaction = new Transaction();
        transaction.date = date;
        transaction.src = src;
        transaction.group = group;
        transaction.action = action;
        transaction.args = args;
        return transaction;
    }
}
