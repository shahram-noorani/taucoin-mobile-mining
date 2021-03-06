package io.taucoin.android.wallet.db.greendao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import io.taucoin.android.wallet.db.entity.KeyValue;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT.
/** 
 * DAO for table "KEY_VALUE".
*/
public class KeyValueDao extends AbstractDao<KeyValue, Long> {

    public static final String TABLENAME = "KEY_VALUE";

    /**
     * Properties of entity KeyValue.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "_id");
        public final static Property Pubkey = new Property(1, String.class, "pubkey", false, "PUBKEY");
        public final static Property Privkey = new Property(2, String.class, "privkey", false, "PRIVKEY");
        public final static Property Address = new Property(3, String.class, "address", false, "ADDRESS");
        public final static Property Balance = new Property(4, long.class, "balance", false, "BALANCE");
        public final static Property Power = new Property(5, long.class, "power", false, "POWER");
        public final static Property NickName = new Property(6, String.class, "nickName", false, "NICK_NAME");
        public final static Property MiningState = new Property(7, String.class, "miningState", false, "MINING_STATE");
        public final static Property TransExpiry = new Property(8, long.class, "transExpiry", false, "TRANS_EXPIRY");
        public final static Property MutableRange = new Property(9, String.class, "mutableRange", false, "MUTABLE_RANGE");
    }


    public KeyValueDao(DaoConfig config) {
        super(config);
    }
    
    public KeyValueDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"KEY_VALUE\" (" + //
                "\"_id\" INTEGER PRIMARY KEY ," + // 0: id
                "\"PUBKEY\" TEXT," + // 1: pubkey
                "\"PRIVKEY\" TEXT," + // 2: privkey
                "\"ADDRESS\" TEXT," + // 3: address
                "\"BALANCE\" INTEGER NOT NULL ," + // 4: balance
                "\"POWER\" INTEGER NOT NULL ," + // 5: power
                "\"NICK_NAME\" TEXT," + // 6: nickName
                "\"MINING_STATE\" TEXT," + // 7: miningState
                "\"TRANS_EXPIRY\" INTEGER NOT NULL ," + // 8: transExpiry
                "\"MUTABLE_RANGE\" TEXT);"); // 9: mutableRange
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"KEY_VALUE\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, KeyValue entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        String pubkey = entity.getPubkey();
        if (pubkey != null) {
            stmt.bindString(2, pubkey);
        }
 
        String privkey = entity.getPrivkey();
        if (privkey != null) {
            stmt.bindString(3, privkey);
        }
 
        String address = entity.getAddress();
        if (address != null) {
            stmt.bindString(4, address);
        }
        stmt.bindLong(5, entity.getBalance());
        stmt.bindLong(6, entity.getPower());
 
        String nickName = entity.getNickName();
        if (nickName != null) {
            stmt.bindString(7, nickName);
        }
 
        String miningState = entity.getMiningState();
        if (miningState != null) {
            stmt.bindString(8, miningState);
        }
        stmt.bindLong(9, entity.getTransExpiry());
 
        String mutableRange = entity.getMutableRange();
        if (mutableRange != null) {
            stmt.bindString(10, mutableRange);
        }
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, KeyValue entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        String pubkey = entity.getPubkey();
        if (pubkey != null) {
            stmt.bindString(2, pubkey);
        }
 
        String privkey = entity.getPrivkey();
        if (privkey != null) {
            stmt.bindString(3, privkey);
        }
 
        String address = entity.getAddress();
        if (address != null) {
            stmt.bindString(4, address);
        }
        stmt.bindLong(5, entity.getBalance());
        stmt.bindLong(6, entity.getPower());
 
        String nickName = entity.getNickName();
        if (nickName != null) {
            stmt.bindString(7, nickName);
        }
 
        String miningState = entity.getMiningState();
        if (miningState != null) {
            stmt.bindString(8, miningState);
        }
        stmt.bindLong(9, entity.getTransExpiry());
 
        String mutableRange = entity.getMutableRange();
        if (mutableRange != null) {
            stmt.bindString(10, mutableRange);
        }
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }    

    @Override
    public KeyValue readEntity(Cursor cursor, int offset) {
        KeyValue entity = new KeyValue( //
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0), // id
            cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1), // pubkey
            cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2), // privkey
            cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3), // address
            cursor.getLong(offset + 4), // balance
            cursor.getLong(offset + 5), // power
            cursor.isNull(offset + 6) ? null : cursor.getString(offset + 6), // nickName
            cursor.isNull(offset + 7) ? null : cursor.getString(offset + 7), // miningState
            cursor.getLong(offset + 8), // transExpiry
            cursor.isNull(offset + 9) ? null : cursor.getString(offset + 9) // mutableRange
        );
        return entity;
    }
     
    @Override
    public void readEntity(Cursor cursor, KeyValue entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setPubkey(cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1));
        entity.setPrivkey(cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2));
        entity.setAddress(cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3));
        entity.setBalance(cursor.getLong(offset + 4));
        entity.setPower(cursor.getLong(offset + 5));
        entity.setNickName(cursor.isNull(offset + 6) ? null : cursor.getString(offset + 6));
        entity.setMiningState(cursor.isNull(offset + 7) ? null : cursor.getString(offset + 7));
        entity.setTransExpiry(cursor.getLong(offset + 8));
        entity.setMutableRange(cursor.isNull(offset + 9) ? null : cursor.getString(offset + 9));
     }
    
    @Override
    protected final Long updateKeyAfterInsert(KeyValue entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }
    
    @Override
    public Long getKey(KeyValue entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(KeyValue entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }
    
}
