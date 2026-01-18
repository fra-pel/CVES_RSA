package com.uvarara.quiz.adapter;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.util.Log;

import com.uvarara.quiz.Constant;
import com.uvarara.quiz.model.Category;
import com.uvarara.quiz.model.Quizplay;
import com.uvarara.quiz.model.SubCategory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;
import net.zetetic.database.sqlcipher.SQLiteConnection;

public class DBHelper extends SQLiteOpenHelper {

    private String packageName;
    private SQLiteDatabase db;

    // nome e versione DB
    private static final String db_name = "quiz_main_sub_cat.db";
    private static int db_version = 2;
    private static int new_db_version=2;
    private static int old_db_version = 2;

    // table names
    public static final String TBL_CATEGORY = "tbl_category";
    public static final String TBL_SUB_CATEGORY = "tbl_subCategory";
    public static final String TBL_QUESTION = "questions_list";
    public static String TBL_LEVEL = "tbl_level";

    // column names
    public static String LEVEL_NO = "level_no";
    public static final String ID = "id";
    public static final String CATE_ID = "cate_id";
    public static final String SUB_CATE_ID = "sub_cate_id";
    public static final String CATEGORY_NAME = "category";
    public static final String SUB_CATEGORY_NAME = "sub_category";
    public static final String QUESTION_SOLUTION = "que_solution";
    public static final String QUESTION = "question";
    public static final String OPTION_A = "option_a";
    public static final String OPTION_B = "option_b";
    public static final String OPTION_C = "option_c";
    public static final String OPTION_D = "option_d";
    public static final String RIGHT_ANSWER = "right_answer";
    public static final String LEVEL = "level";

    private String db_path;
    private String DB_PASSWORD;
    Context con;

    // 🔹 carichiamo la libreria SQLCipher e quella nativa dove è frammentata la chiave
    static {
        System.loadLibrary("sqlcipher");
        System.loadLibrary("ksec"); // la libreria nativa con i frammenti della chiave
    }

    // metodo nativo che ricostruisce la chiave in RAM
    private static native byte[] getDbKeyNative();

    public DBHelper(Context con) {
        super(con, db_name, null, db_version);
        this.con = con;
        db_path = con.getDatabasePath(db_name).getAbsolutePath();

        // Recupera la chiave dal nativo, la converte in String, poi pulisce il buffer
        byte[] keyBytes = getDbKeyNative();
        DB_PASSWORD = new String(keyBytes, StandardCharsets.UTF_8);
        java.util.Arrays.fill(keyBytes, (byte) 0); // pulizia in RAM
    }
    @Override
    public void onCreate(SQLiteDatabase db) {}

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public void createDB() throws IOException {
        Log.v("DBHelper","Controlliamo se c'è il db");
        if (checkDB()) {
            Log.v("DBHelper","Controllo versione");
            if (new_db_version > old_db_version) {
                Log.v("DBHelper","Database version higher than old.");
                String fileName = db_path;
                File myFile = new File(fileName);
                myFile.delete();
                Log.v("DBHelper","Database cancellato");
                this.getReadableDatabase();
                close();
                Log.v("DBHelper","Inizio copia Database");
                copyDB();
                Log.v("DBHelper","Database copiato da asset");
            }
            else  {
                Log.v("DBHelper","Database OK.");
            }
        } else if (!checkDB()) {
            Log.v("DBHelper","Database non esistente");
            this.getReadableDatabase();
            close();
            copyDB();
        }
    }

    private boolean checkDB() {
        Log.v("DBHelper", "🔍 Inizio controllo DB");

        SQLiteDatabase cDB = null;
        boolean isValid = false;

        try {
            cDB = SQLiteDatabase.openOrCreateDatabase(db_path, DB_PASSWORD, null, null, getHook());

            if (cDB != null) {
                // Recupera tutte le tabelle
                Cursor tablesCursor = cDB.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
                if (tablesCursor != null) {
                    if (tablesCursor.moveToFirst()) {
                        isValid = true;
                        do {
                            String tableName = tablesCursor.getString(0);
                            Log.v("DBHelper", "📁 Tabella trovata: " + tableName);

                            // Recupera le colonne della tabella
                            Cursor columnsCursor = cDB.rawQuery("PRAGMA table_info(" + tableName + ")", null);
                            if (columnsCursor != null && columnsCursor.moveToFirst()) {
                                Log.v("DBHelper", "📌 Colonne di " + tableName + ":");
                                do {
                                    String columnName = columnsCursor.getString(columnsCursor.getColumnIndexOrThrow("name"));
                                    String columnType = columnsCursor.getString(columnsCursor.getColumnIndexOrThrow("type"));
                                    Log.v("DBHelper", "   • " + columnName + " (" + columnType + ")");
                                } while (columnsCursor.moveToNext());
                                columnsCursor.close();
                            }
                        } while (tablesCursor.moveToNext());
                    } else {
                        Log.v("DBHelper", "⚠️ Nessuna tabella trovata nel DB");
                    }
                    tablesCursor.close();
                }
            }
        } catch (SQLiteException e) {
            Log.e("DBHelper", "❌ Errore nell'apertura del DB: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (cDB != null) {
                cDB.close();
            }
        }

        Log.v("DBHelper", "🔚 Fine controllo DB. Esito: " + isValid);
        return isValid;
    }


    private void copyDB() throws IOException {
        Log.v("DBHelper","CopyDb");
        InputStream inputFile = con.getAssets().open(db_name);
        OutputStream outFile = new FileOutputStream(db_path);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputFile.read(buffer)) > 0) {
            outFile.write(buffer, 0, length);
        }
        outFile.flush();
        outFile.close();
        inputFile.close();
    }

    private SQLiteDatabaseHook getHook() {
        return new SQLiteDatabaseHook() {
            @Override
            public void preKey(SQLiteConnection connection) {
                // Nessuna operazione necessaria
            }

            @Override
            public void postKey(SQLiteConnection connection) {
                //connection.execute("PRAGMA cipher_migrate;", null, null);
            }
        };
    }


    @Override
    public SQLiteDatabase getReadableDatabase() {
        return SQLiteDatabase.openDatabase(db_path, DB_PASSWORD, null, SQLiteDatabase.OPEN_READONLY, getHook());
    }

    //public SQLiteDatabase getReadableDatabase(String password) {
    //    return SQLiteDatabase.openDatabase(db_path, password, null, SQLiteDatabase.OPEN_READONLY, getHook());
    // }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        return SQLiteDatabase.openDatabase(db_path, DB_PASSWORD, null, SQLiteDatabase.OPEN_READWRITE, getHook());
    }

    /*
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO Auto-generated method stub
            // TODO FRANCESCO UPGRADE DB
            if (newVersion > oldVersion) {
                Log.v("Database Upgrade", "Database version higher than old.");
                File folder = Environment.getExternalStorageDirectory();
                String fileName = db_path;
                File myFile = new File(fileName);
                if(myFile.exists()) {
                    myFile.delete();
                    Log.v("Database deleted", "Database deleted.");
                }
            }
        }
     */
    // FINE FRANCESCO
    /*
     *get All category from table
     */
    public ArrayList<Category> getAllCategories() {
        Log.v("DBHelper","getAllCategories");
        Log.v("DBHelper", "Percorso DB: " + db_path);
        File dbFile = new File(db_path);
        if (!dbFile.exists()) {
            Log.e("DBHelper", "Database file non trovato: " + db_path);
        } else {
            Log.v("DBHelper", "Database trovato: " + db_path);
        }
        File dbDir = new File(con.getDatabasePath("dummy").getParent());
        File[] files = dbDir.listFiles();
        if (files != null) {
            for (File f : files) {
                Log.v("DBHelper", "File trovato: " + f.getName());
            }
        }
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(db_path, DB_PASSWORD, null, SQLiteDatabase.OPEN_READONLY, getHook());
            Log.v("DBHelper", "Database aperto correttamente");
            db.close(); // ✅ aggiunto
        } catch (Exception e) {
            Log.e("DBHelper", "Errore nell'apertura del DB: " + e.getMessage());
        }

        SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<Category> categoryArrayList = new ArrayList<>();
        Cursor cur = db.rawQuery("SELECT * FROM  " + TBL_CATEGORY, null);
        if (cur.moveToFirst()) {
            do {
                Category category = new Category();
                category.setId(cur.getInt(cur.getColumnIndexOrThrow(ID)));
                category.setName(cur.getString(cur.getColumnIndexOrThrow(CATEGORY_NAME)));
                categoryArrayList.add(category);

            } while (cur.moveToNext());
        }
        //}
        cur.close(); // ✅ aggiunto
        db.close(); // ✅ aggiunto
        return categoryArrayList;
    }

    public int GetMaxLevelSingleCat(int cat_id) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cur = db.rawQuery("select max(" + LEVEL + ") from " + TBL_QUESTION + " where (" + CATE_ID + "=" + cat_id + ")", null);
        if (cur.moveToFirst()) {
            do {
                Constant.totalLevel = cur.getInt(cur.getColumnIndexOrThrow("max(level)"));
            } while (cur.moveToNext());
        }
        //}
        cur.close(); // ✅ aggiunto
        db.close(); // ✅ aggiunto
        return Constant.totalLevel;
    }

    public int GetMaxLevel(int cat_id, int sub_cate_id) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cur = db.rawQuery("select max(" + LEVEL + ") from " + TBL_QUESTION + " where (" + CATE_ID + "=" + cat_id + " and " + SUB_CATE_ID + "=" + sub_cate_id + ")", null);
        if (cur.moveToFirst()) {
            do {
                Constant.totalLevel = cur.getInt(cur.getColumnIndexOrThrow("max(level)"));
            } while (cur.moveToNext());
        }
        //}
        return Constant.totalLevel;
    }

    public ArrayList<SubCategory> getSubCategoryById(int cate_id) {
        SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<SubCategory> subCategories = new ArrayList<>();
        Cursor cur = db.rawQuery("SELECT * FROM  " + TBL_SUB_CATEGORY + " where (" + CATE_ID + " = " + cate_id + ")", null);
        if (cur.moveToFirst()) {
            do {
                SubCategory subCategory = new SubCategory();
                subCategory.setId(cur.getInt(cur.getColumnIndexOrThrow(ID)));
                subCategory.setCategoryId(cur.getString(cur.getColumnIndexOrThrow(CATE_ID)));
                subCategory.setName(cur.getString(cur.getColumnIndexOrThrow(SUB_CATEGORY_NAME)));
                subCategories.add(subCategory);

            } while (cur.moveToNext());
        }
        //}
        cur.close(); // ✅ aggiunto
        db.close(); // ✅ aggiunto
        return subCategories;
    }

    public List<Quizplay> getQuestionGujSingleCat(int cate_id, int noOfQuestion, int level) {

        List<Quizplay> quizplay = new ArrayList<Quizplay>();
        int total = noOfQuestion;
        String sql = "select *  FROM " + TBL_QUESTION + " where (" + CATE_ID + "=" + cate_id + " and "
                + LEVEL + "=" + level + ") ORDER BY RANDOM() LIMIT " + total;
        SQLiteDatabase db = this.getReadableDatabase();
        //SQLiteDatabase db = SQLiteDatabase.openDatabase("/data/data/" + packageName + "/databases/" + DATABASE_NAME, null, 0);
        Cursor cursor = db.rawQuery(sql, null);

        if (cursor.moveToFirst()) {
            do {
                Quizplay question = new Quizplay();
                question.setId(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                question.setQuestion(cursor.getString(cursor.getColumnIndexOrThrow("question")));
                question.addOption(cursor.getString(cursor.getColumnIndexOrThrow("option_a")));
                question.addOption(cursor.getString(cursor.getColumnIndexOrThrow("option_b")));
                question.addOption(cursor.getString(cursor.getColumnIndexOrThrow("option_c")));
                question.addOption(cursor.getString(cursor.getColumnIndexOrThrow("option_d")));
                String rightAns = cursor.getString(cursor.getColumnIndexOrThrow("right_answer"));
                if (rightAns.equalsIgnoreCase("A")) {
                    question.setTrueAns(cursor.getString(cursor.getColumnIndexOrThrow("option_a")));
                } else if (rightAns.equalsIgnoreCase("B")) {
                    question.setTrueAns(cursor.getString(cursor.getColumnIndexOrThrow("option_b")));
                } else if (rightAns.equalsIgnoreCase("C")) {
                    question.setTrueAns(cursor.getString(cursor.getColumnIndexOrThrow("option_c")));
                } else {
                    question.setTrueAns(cursor.getString(cursor.getColumnIndexOrThrow("option_d")));
                }
                if (question.getOptions().size() == 4) {
                    quizplay.add(question);
                }

            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        Collections.shuffle(quizplay);
        quizplay = quizplay.subList(0, noOfQuestion);
        return quizplay;
    }
    public List<Quizplay> getQuestionGuj(int cate_id, int sub_cate_id, int noOfQuestion, int level) {

        List<Quizplay> quizplay = new ArrayList<Quizplay>();
        int total = noOfQuestion;
        String sql = "select *  FROM " + TBL_QUESTION + " where (" + CATE_ID + "=" + cate_id + " and "
                + SUB_CATE_ID + " =" + sub_cate_id + " and "
                + LEVEL + "=" + level + ") ORDER BY RANDOM() LIMIT " + total;
        SQLiteDatabase db = this.getReadableDatabase();
        //SQLiteDatabase db = SQLiteDatabase.openDatabase("/data/data/" + packageName + "/databases/" + DATABASE_NAME, null, 0);
        Cursor cursor = db.rawQuery(sql, null);

        if (cursor.moveToFirst()) {
            do {
                Quizplay question = new Quizplay();
                question.setId(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                question.setQuestion(cursor.getString(cursor.getColumnIndexOrThrow("question")));
                question.addOption(cursor.getString(cursor.getColumnIndexOrThrow("option_a")));
                question.addOption(cursor.getString(cursor.getColumnIndexOrThrow("option_b")));
                question.addOption(cursor.getString(cursor.getColumnIndexOrThrow("option_c")));
                question.addOption(cursor.getString(cursor.getColumnIndexOrThrow("option_d")));
                String rightAns = cursor.getString(cursor.getColumnIndexOrThrow("right_answer"));
                if (rightAns.equalsIgnoreCase("A")) {
                    question.setTrueAns(cursor.getString(cursor.getColumnIndexOrThrow("option_a")));
                } else if (rightAns.equalsIgnoreCase("B")) {
                    question.setTrueAns(cursor.getString(cursor.getColumnIndexOrThrow("option_b")));
                } else if (rightAns.equalsIgnoreCase("C")) {
                    question.setTrueAns(cursor.getString(cursor.getColumnIndexOrThrow("option_c")));
                } else {
                    question.setTrueAns(cursor.getString(cursor.getColumnIndexOrThrow("option_d")));
                }
                if (question.getOptions().size() == 4) {
                    quizplay.add(question);
                }

            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        Collections.shuffle(quizplay);
        quizplay = quizplay.subList(0, noOfQuestion);
        return quizplay;
    }


    /*
     * insert level no
     */
    public void insertIntoDBSingleCat(int cat_id,int level_no) {
        SQLiteDatabase db = this.getWritableDatabase();
        String query = "INSERT INTO " + TBL_LEVEL + " (" + CATE_ID + "," + LEVEL_NO + ") VALUES('" + cat_id + "', '" + level_no + "');";
        db.execSQL(query);
        db.close(); // ✅ aggiunto

    }
    public void insertIntoDB(int cat_id, int sub_cat_id, int level_no) {
        SQLiteDatabase db = this.getWritableDatabase();
        String query = "INSERT INTO " + TBL_LEVEL + " (" + CATE_ID + "," + SUB_CATE_ID + "," + LEVEL_NO + ") VALUES('" + cat_id + "', '" + sub_cat_id + "', '" + level_no + "');";
        db.execSQL(query);
        db.close(); // ✅ aggiunto
    }

    /*
     *with this method we check if categoryId & subCategoryId is already exist or not in our database
     */
    public boolean isExistSingleCat(int cat_id) {
        db = this.getReadableDatabase();
        Cursor cur = db.rawQuery("SELECT * FROM " + TBL_LEVEL + " WHERE ( " + CATE_ID + " = " + cat_id + ")", null);
        boolean exist = (cur.getCount() > 0);
        cur.close();
        db.close(); // ✅ aggiunto
        return exist;

    }
    public boolean isExist(int cat_id, int sub_cat_id) {
        db = this.getReadableDatabase();
        Cursor cur = db.rawQuery("SELECT * FROM " + TBL_LEVEL + " WHERE ( " + CATE_ID + " = " + cat_id + " AND " + SUB_CATE_ID + " = " + sub_cat_id + ")", null);
        boolean exist = (cur.getCount() > 0);
        cur.close();
        db.close(); // ✅ aggiunto
        return exist;

    }

    /*
     * get level
     */
    public int GetLevelByIdUsingSingleCat(int cat_id) {
        int level = 1;
        String selectQuery = "SELECT  * FROM " + TBL_LEVEL + " WHERE  (" + CATE_ID + "=" + cat_id + ")";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(selectQuery, null);
        // looping through all rows and adding to list
        if (c.moveToFirst()) {
            do {
                level = c.getInt(c.getColumnIndexOrThrow(LEVEL_NO));
            } while (c.moveToNext());
        }
        c.close(); // ✅ aggiunto
        db.close(); // ✅ aggiunto
        return level;
    }

    public int GetLevelById(int cat_id, int sub_cat_id) {
        int level = 1;
        String selectQuery = "SELECT  * FROM " + TBL_LEVEL + " WHERE  (" + CATE_ID + "=" + cat_id + " AND " + SUB_CATE_ID + "=" + sub_cat_id + ")";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(selectQuery, null);
        // looping through all rows and adding to list
        if (c.moveToFirst()) {
            do {
                level = c.getInt(c.getColumnIndexOrThrow(LEVEL_NO));
            } while (c.moveToNext());
        }
        c.close(); // ✅ aggiunto
        db.close(); // ✅ aggiunto
        return level;
    }

    public String getQuestionSolution(int queId) {
        String level = "";
        String selectQuery = "SELECT  * FROM " + TBL_QUESTION + " WHERE  (" + ID + "=" + queId + ")";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(selectQuery, null);
        // looping through all rows and adding to list
        if (c.moveToFirst()) {
            do {
                level = c.getString(c.getColumnIndexOrThrow(QUESTION_SOLUTION));
            } while (c.moveToNext());
        }
        c.close(); // ✅ aggiunto
        db.close(); // ✅ aggiunto
        return level;
    }

    /*
     * Update level
     */
//    public void UpdateLevelSingleCat(int cat_id, int level_no) {
//        db = this.getReadableDatabase();
//
//        db.execSQL("update " + TBL_LEVEL + " set level_no=" + level_no + " where (" + CATE_ID + "=" + cat_id + ")");
//    }
    public void UpdateLevel(int cat_id, int sub_cat_id, int level_no) {
        db = this.getReadableDatabase();
        db.execSQL("update " + TBL_LEVEL + " set level_no=" + level_no + " where (" + CATE_ID + "=" + cat_id + "  and  " + SUB_CATE_ID + " = " + sub_cat_id + ")");
        db.close(); // ✅ aggiunto
    }
}
