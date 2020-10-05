package se.splushii.dancingbunnies.storage.db;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class TransactionDao {
    @Insert(onConflict = REPLACE)
    public abstract void insert(Transaction... transactions);

    @Query("DELETE FROM " + DB.TABLE_LIBRARY_TRANSACTIONS
            + " WHERE " + Transaction.COLUMN_ROW_ID + " = :rowId;")
    public abstract void delete(long rowId);

    @androidx.room.Transaction
    public void delete(List<Long> rowIds) {
        for (long rowId: rowIds) {
            delete(rowId);
        }
    }

    @Query("SELECT * FROM " + DB.TABLE_LIBRARY_TRANSACTIONS
            + " ORDER BY " + Transaction.COLUMN_DATE + " ASC;")
    public abstract LiveData<List<Transaction>> getTransactions();
    @Query("SELECT * FROM " + DB.TABLE_LIBRARY_TRANSACTIONS
            + " ORDER BY " + Transaction.COLUMN_DATE + " ASC;")
    public abstract List<Transaction> getTransactionsOnce();
    @Query("SELECT * FROM " + DB.TABLE_LIBRARY_TRANSACTIONS
            + " WHERE " + Transaction.COLUMN_SRC + " = :src"
            + " AND " + Transaction.COLUMN_GROUP + " = :group"
            + " ORDER BY " + Transaction.COLUMN_DATE + " ASC;")
    public abstract List<Transaction> getTransactionsOnce(String src, String group);
    @Query("SELECT * FROM " + DB.TABLE_LIBRARY_TRANSACTIONS
            + " WHERE " + Transaction.COLUMN_SRC + " = :src"
            + " ORDER BY " + Transaction.COLUMN_DATE + " ASC;")
    public abstract LiveData<List<Transaction>> getTransactions(String src);

    @Query("UPDATE " + DB.TABLE_LIBRARY_TRANSACTIONS
            + " SET " + Transaction.COLUMN_ERROR + " = :error"
            + ", " + Transaction.COLUMN_ERROR_NUM + " = " + Transaction.COLUMN_ERROR_NUM + " + 1"
            + " WHERE " + Transaction.COLUMN_ROW_ID + " = :id;")
    public abstract void setError(long id, String error);
}