package com.example.visio_conduits.utils;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

public class DBHelper extends SQLiteOpenHelper {
  public static final String DATABASE_NAME = "KleitzElec.db";

  public static final String ROOMS_TABLE_CREATE = "CREATE TABLE rooms (_id INTEGER PRIMARY KEY AUTOINCREMENT, name STRING )";

  public static final String ROOMS_TABLE_DROP = "DROP TABLE IF EXISTS rooms;";

  public static final String NAMES_TABLE_CREATE = "CREATE TABLE names (_id INTEGER PRIMARY KEY AUTOINCREMENT, name STRING )";

  public static final String NAMES_TABLE_DROP = "DROP TABLE IF EXISTS names;";

  public static final String ROOMS_TABLE_NAME = "rooms";

  public static final String ROOM_COLUMN_ID = "_id";

  public static final String ROOM_COLUMN_NAME = "name";

  public static final String TAGS_TABLE_CREATE = "CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT, uii STRING, name INTEGER,room INTEGER, workplace INTEGER )";

  public static final String TAGS_TABLE_DROP = "DROP TABLE IF EXISTS tags;";

  public static final String TAGS_TABLE_NAME = "tags";

  public static final String TAG_COLUMN_ID = "id";

  public static final String TAG_COLUMN_NAME = "name";

  public static final String TAG_COLUMN_ROOM_NAME_ID = "room";

  public static final String TAG_COLUMN_UII = "uii";

  public static final String TAG_COLUMN_WORKPLACE_NAME_ID = "workplace";

  public static final int VERSION = 1;

  public static final String WORKPLACES_TABLE_CREATE = "CREATE TABLE workplaces (_id INTEGER PRIMARY KEY AUTOINCREMENT, name STRING )";

  public static final String WORKPLACES_TABLE_DROP = "DROP TABLE IF EXISTS workplaces;";

  public static final String WORKPLACES_TABLE_NAME = "workplaces";

  public static final String WORKPLACE_COLUMN_ID = "_id";

  public static final String WORKPLACE_COLUMN_NAME = "name";

  private final Activity mActivity;

  public boolean isCreating = false;
  public SQLiteDatabase currentDB = null;

  /**
   * Constructor - takes the context to allow the database to be
   * opened/created
   *
   * @param activity
   *            the Activity that is using the database
   */


  public DBHelper(Context paramContext, String s, SQLiteDatabase.CursorFactory paramCursorFactory, int paramInt, Activity activity) {
    super(paramContext, DATABASE_NAME, paramCursorFactory, paramInt);
    this.mActivity = activity;
  }

  @Override
  public void onCreate(SQLiteDatabase paramSQLiteDatabase) {
    System.out.println("Create DB");
    paramSQLiteDatabase.execSQL(TAGS_TABLE_CREATE);
    paramSQLiteDatabase.execSQL(NAMES_TABLE_CREATE);
    paramSQLiteDatabase.execSQL(WORKPLACES_TABLE_CREATE);
    paramSQLiteDatabase.execSQL(ROOMS_TABLE_CREATE);
    isCreating = true;
    currentDB = paramSQLiteDatabase;
    insertName("PCT");
    insertName("RJ45");
    insertName("TV");
    insertName("HDMI");
    insertName("Téléphone");
    insertName("Bouton va et vient");
    insertName("Bouton SA");
    insertName("Bouton poussoir");
    insertName("Bouton commutateur");
    insertName("Point lumineux plafond");
    insertName("Point lumineux applique");

    insertRoom("Cuisine");
    insertRoom("Salon");
    insertRoom("Séjour");
    insertRoom("Buanderie");
    insertRoom("Terrasse");
    insertRoom("Couloir");
    insertRoom("Entrée");
    insertRoom("Salle de bain");
    insertRoom("WC");
    insertRoom("Toilettes");
    insertRoom("Chambre n°1");
    insertRoom("Chambre n°2");
    insertRoom("Chambre n°3");
    insertRoom("Chambre n°4");
    insertRoom("Chambre n°5");
    insertRoom("Chambre n°6");

    // release var
    isCreating = false;
    currentDB = null;
  }

  @Override
  public SQLiteDatabase getWritableDatabase() {

    if(isCreating && currentDB != null){
      return currentDB;
    }
    return super.getWritableDatabase();
  }

  @Override
  public SQLiteDatabase getReadableDatabase() {

    if(isCreating && currentDB != null){
      return currentDB;
    }
    return super.getReadableDatabase();
  }

  public void onUpgrade(SQLiteDatabase paramSQLiteDatabase, int paramInt1, int paramInt2) {
    paramSQLiteDatabase.execSQL(ROOMS_TABLE_DROP);
    paramSQLiteDatabase.execSQL(NAMES_TABLE_DROP);
    paramSQLiteDatabase.execSQL(TAGS_TABLE_DROP);
    paramSQLiteDatabase.execSQL(WORKPLACES_TABLE_DROP);
    onCreate(paramSQLiteDatabase);
  }

  public ArrayList<String> getAllTags() {
    ArrayList<String> arrayList = new ArrayList();
    Cursor cursor = getReadableDatabase().rawQuery("select * from tags", null);
    cursor.moveToFirst();
    while (!cursor.isAfterLast()) {
      arrayList.add(cursor.getString(cursor.getColumnIndex("name")));
      cursor.moveToNext();
    }
    return arrayList;
  }

  public int numberOfRows() {
    return (int) DatabaseUtils.queryNumEntries(getReadableDatabase(), "tags");
  }

  public boolean insertTag(String uii, String name, String room, String workplace) {
    int nameId=1;
    int roomId=1;
    int workplaceId=1;

    Cursor cursor1 = selectARoom(room);
    if (!cursor1.moveToFirst() || cursor1.getCount() == 0) {
      insertRoom(room);
      Cursor cursor2 = selectARoom(room);
      if (cursor2.moveToFirst() && cursor2.getCount() != 0) {
        roomId= cursor2.getInt(cursor2.getColumnIndex("_id"));
      }
    }
    else{
      cursor1.moveToFirst();
      roomId= cursor1.getInt(cursor1.getColumnIndex("_id"));
    }

    System.out.println("roomId:"+roomId);
    Cursor cursor3 = selectAWorkPlace(workplace);
    if (!cursor3.moveToFirst() || cursor3.getCount() == 0) {
      insertWorkplace(workplace);
      Cursor cursor4 = selectAWorkPlace(workplace);
      if (cursor4.moveToFirst() && cursor4.getCount() != 0) {
        workplaceId = cursor4.getInt(cursor4.getColumnIndex("_id"));
      }
    }
    else{
      cursor3.moveToFirst();
      workplaceId= cursor3.getInt(cursor3.getColumnIndex("_id"));
    }
    System.out.println("workplaceId:"+workplaceId);

    Cursor cursor5 = selectAName(name);
    if (!cursor5.moveToFirst() || cursor5.getCount() == 0) {
      insertName(name);
      Cursor cursor6 = selectAName(workplace);
      if (cursor6.moveToFirst() && cursor6.getCount() != 0) {
        nameId = cursor6.getInt(cursor6.getColumnIndex("_id"));
      }
    }
    else{
      cursor5.moveToFirst();
      nameId= cursor5.getInt(cursor5.getColumnIndex("_id"));
    }
    System.out.println("nameId:"+nameId);

    ContentValues contentValues = new ContentValues();
    System.out.println("uii:"+uii);

    contentValues.put("uii", uii);
    contentValues.put("name", nameId);
    contentValues.put("room", roomId);
    contentValues.put("workplace", workplaceId);
    SQLiteDatabase mDb = this.getWritableDatabase();
    mDb.insert("tags", null, contentValues);
    return true;
  }

  public boolean insertName(String name) {
    System.out.println("insertName");
    ContentValues contentValues = new ContentValues();
    contentValues.put("name", name);
    SQLiteDatabase mDb = this.getWritableDatabase();
    mDb.insert("names", null, contentValues);
    return true;
  }

  public boolean insertRoom(String name) {
    System.out.println("insertRoom");
    ContentValues contentValues = new ContentValues();
    contentValues.put("name", name);
    SQLiteDatabase mDb = this.getWritableDatabase();
    mDb.insert("rooms", null, contentValues);
    return true;
  }

  public boolean insertWorkplace(String name) {
    System.out.println("insertWorkplace");
    ContentValues contentValues = new ContentValues();
    contentValues.put("name", name);
    SQLiteDatabase mDb = this.getWritableDatabase();
    mDb.insert("workplaces", null, contentValues);
    return true;
  }

  public Cursor selectATag(String uii) {
    String sql="select tags.uii, names.name as name, rooms.name as room, workplaces.name as workplace from tags,workplaces,rooms,names where uii= ? and tags.name=names._id and tags.room=rooms._id and tags.workplace=workplaces._id";
    SQLiteDatabase mDb = this.getWritableDatabase();
    return mDb.rawQuery(sql,new String[]{uii});
  }

  public Cursor selectAName(String name) {
    String sql="select * from names where name= ? ";
    SQLiteDatabase mDb = this.getWritableDatabase();
    return mDb.rawQuery(sql,new String[]{name});
  }

  public Cursor selectARoom(String name) {
    String sql="select * from rooms where name= ? ";
    SQLiteDatabase mDb = this.getWritableDatabase();
    return mDb.rawQuery(sql,new String[]{name});
  }

  public Cursor selectAWorkPlace(String name) {
    String sql="select * from workplaces where name= ? ";
    SQLiteDatabase mDb = this.getWritableDatabase();
    return mDb.rawQuery(sql,new String[]{name});
  }

  public boolean updateTag(String uii, String name, String room, String workplace) {
    int roomId=1;
    int workplaceId=1;
    int nameId=1;
    Cursor cursor1 = selectARoom(room);
    if (!cursor1.moveToFirst() || cursor1.getCount() == 0) {
      insertRoom(room);
      Cursor cursor2 = selectARoom(room);
      if (cursor2.moveToFirst() && cursor2.getCount() != 0) {
        roomId= cursor2.getInt(cursor2.getColumnIndex("_id"));
      }
    }
    else{
      cursor1.moveToFirst();
      roomId= cursor1.getInt(cursor1.getColumnIndex("_id"));
    }

    System.out.println("roomId:"+roomId);
    Cursor cursor3 = selectAWorkPlace(workplace);
    if (!cursor3.moveToFirst() || cursor3.getCount() == 0) {
      insertWorkplace(workplace);
      Cursor cursor4 = selectAWorkPlace(workplace);
      if (cursor4.moveToFirst() && cursor4.getCount() != 0) {
        workplaceId = cursor4.getInt(cursor4.getColumnIndex("_id"));
      }
    }
    else{
      cursor3.moveToFirst();
      workplaceId= cursor3.getInt(cursor3.getColumnIndex("_id"));
    }

    System.out.println("workplaceId:"+workplaceId);
    Cursor cursor5 = selectAName(name);
    if (!cursor5.moveToFirst() || cursor5.getCount() == 0) {
      insertName(name);
      Cursor cursor6 = selectAName(name);
      if (cursor6.moveToFirst() && cursor6.getCount() != 0) {
        nameId = cursor6.getInt(cursor6.getColumnIndex("_id"));
      }
    }
    else{
      cursor5.moveToFirst();
      nameId= cursor5.getInt(cursor5.getColumnIndex("_id"));
    }
    System.out.println("nameId:"+nameId);

    ContentValues contentValues = new ContentValues();
    contentValues.put("uii", uii);
    contentValues.put("name", nameId);
    contentValues.put("room", roomId);
    contentValues.put("workplace", workplaceId);
    SQLiteDatabase mDb = this.getWritableDatabase();
    mDb.update("tags", contentValues, "uii = ? ", new String[] { uii });
    return true;
  }


  /**
   * Return a Cursor that returns all states (and their state capitals) where
   * the state name begins with the given constraint string.
   *
   * @param constraint
   *            Specifies the first letters of the states to be listed. If
   *            null, all rows are returned.
   * @return Cursor managed and positioned to the first state, if found
   * @throws SQLException
   *             if query fails
   */
  public Cursor getMatchingStates(String constraint, String type) throws SQLException {
    System.out.println("type:"+type);
    System.out.println(type.equals("room"));
    String queryString="";
  if (type.equals("workplaces")){//passe pas la condition ici
     queryString = "SELECT _id,name FROM workplaces";
  }
  else if (type.equals("rooms")){
    queryString = "SELECT _id,name FROM rooms";
  }
  else if (type.equals("names")){
    queryString = "SELECT _id,name FROM names";
  }

    if (constraint != null) {
      // Query for any rows where the state name begins with the
      // string specified in constraint.
      constraint = constraint.trim() + "%";
      queryString += " WHERE name LIKE ?";
    }
    System.out.println("yo:"+queryString);//ici
    String[] params = { constraint };

    if (constraint == null) {
      // If no parameters are used in the query,
      // the params arg must be null.
      params = null;
    }
    try {
      SQLiteDatabase mDb = this.getWritableDatabase();
      Cursor cursor = mDb.rawQuery(queryString, params);
      if (cursor != null) {
        this.mActivity.startManagingCursor(cursor);
        cursor.moveToFirst();
        return cursor;
      }
    }
    catch (SQLException e) {
      Log.e("AutoCompleteDbAdapter", e.toString());
      throw e;
    }

    return null;
  }


}
