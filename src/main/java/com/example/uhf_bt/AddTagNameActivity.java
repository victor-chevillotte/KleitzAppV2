package com.example.uhf_bt;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.TextView;

import com.example.uhf_bt.utils.DBHelper;

public class AddTagNameActivity extends BaseActivity{

    public String uiiOfFocus;
    public String name="";
    public String room="";
    public String workplace="";
    public Boolean newTag=true;

    private DBHelper mydb = new DBHelper(this, "KleitzElec.db", null, 1,this);
    public AutoCompleteTextView mStatWorkplaceView;
    public AutoCompleteTextView mStatRoomView;
    public AutoCompleteTextView mStatNameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_tag_name);

        Intent intent = getIntent();
        uiiOfFocus = intent.getStringExtra("uii");
        TextView uiiDisplay = findViewById(R.id.AddTagUii);
        uiiDisplay.setText(uiiOfFocus);
        name = intent.getStringExtra("name");
        AutoCompleteTextView AddTagName = findViewById(R.id.AddTagName);
        AddTagName.setText(name);
        room = intent.getStringExtra("room");
        AutoCompleteTextView AddTagRoom = findViewById(R.id.AddTagRoom);
        AddTagRoom.setText(room);
        workplace = intent.getStringExtra("workplace");
        AutoCompleteTextView AddTagWorkplace = findViewById(R.id.AddTagWorkplace);
        AddTagWorkplace.setText(workplace);
        newTag = intent.getBooleanExtra("newTag", Boolean.parseBoolean("true"));
        if(!newTag){
            Button addModifyTagBtn = (Button) findViewById(R.id.AddTagSubmitBtn);
            addModifyTagBtn.setText("Modifier");
        }

        mStatWorkplaceView = findViewById(R.id.AddTagWorkplace);
        mStatRoomView = findViewById(R.id.AddTagRoom);
        mStatNameView = findViewById(R.id.AddTagName);

        // Create an ItemAutoTextAdapter for the State Name field,
        // and set it as the OnItemClickListener for that field.

        ItemAutoTextAdapter adapterName = this.new ItemAutoTextAdapter(mydb, "names");
        mStatNameView.setAdapter(adapterName);
        mStatNameView.setOnItemClickListener(adapterName);

        ItemAutoTextAdapter adapter = this.new ItemAutoTextAdapter(mydb, "workplaces");
        mStatWorkplaceView.setAdapter(adapter);
        mStatWorkplaceView.setOnItemClickListener(adapter);

        ItemAutoTextAdapter adapterRoom = this.new ItemAutoTextAdapter(mydb, "rooms");
        mStatRoomView.setAdapter(adapterRoom);
        mStatRoomView.setOnItemClickListener(adapterRoom);

    }

    public void AddTagbuttonHandler(View view) {
        Button addModifyTagBtn = (Button) findViewById(R.id.AddTagSubmitBtn);
        if(addModifyTagBtn.getText()=="Modifier"){
            //Decide what happens when the user clicks the Modify Tag button
            TextView uiiTextView = findViewById(R.id.AddTagUii);
            String tagUii = uiiTextView.getText().toString();

            EditText tagNameEditText = findViewById(R.id.AddTagName);
            String tagName = tagNameEditText.getText().toString();

            EditText tagRoomEditText = findViewById(R.id.AddTagRoom);
            String tagRoom = tagRoomEditText.getText().toString();

            EditText tagWorkplaceEditText = findViewById(R.id.AddTagWorkplace);
            String tagWorkplace = tagWorkplaceEditText.getText().toString();
            if (tagName.equals("")){
                TextView tagNameAlert = findViewById(R.id.AddTagNameAlert);
                tagNameAlert.setText("Veuillez entrer un nom.");
                return;
            }
            if (tagRoom.equals("")){
                TextView tagRoomAlert = findViewById(R.id.AddTagRoomAlert);
                tagRoomAlert.setText("Veuillez entrer une pièce.");
                return;
            }
            if (tagWorkplace.equals("")){
                TextView tagWorkplaceAlert = findViewById(R.id.AddTagWorkplaceAlert);
                tagWorkplaceAlert.setText("Veuillez entrer un chantier.");
                return;
            }
            mydb.updateTag(tagUii,tagName,tagRoom,tagWorkplace);
        }
        else {
            //Decide what happens when the user clicks the Add Tag button
            TextView uiiTextView = findViewById(R.id.AddTagUii);
            String tagUii = uiiTextView.getText().toString();

            EditText tagNameEditText = findViewById(R.id.AddTagName);
            String tagName = tagNameEditText.getText().toString();

            EditText tagRoomEditText = findViewById(R.id.AddTagRoom);
            String tagRoom = tagRoomEditText.getText().toString();

            EditText tagWorkplaceEditText = findViewById(R.id.AddTagWorkplace);
            String tagWorkplace = tagWorkplaceEditText.getText().toString();
            if (tagName.equals("")){
                TextView tagNameAlert = findViewById(R.id.AddTagNameAlert);
                tagNameAlert.setText("Veuillez entrer un nom.");
                return;
            }
            if (tagRoom.equals("")){
                TextView tagRoomAlert = findViewById(R.id.AddTagRoomAlert);
                tagRoomAlert.setText("Veuillez entrer une pièce.");
                return;
            }
            if (tagWorkplace.equals("")){
                TextView tagWorkplaceAlert = findViewById(R.id.AddTagWorkplaceAlert);
                tagWorkplaceAlert.setText("Veuillez entrer un chantier.");
                return;
            }
            mydb.insertTag(tagUii,tagName,tagRoom,tagWorkplace);
        }
        finish();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Specializes CursorAdapter to supply choices to a AutoCompleteTextView.
     * Also implements OnItemClickListener to be notified when a choice is made,
     * and uses the choice to update other fields on the Activity form.
     */
    class ItemAutoTextAdapter extends CursorAdapter implements android.widget.AdapterView.OnItemClickListener {

        public String typeOfField;

        /**
         * Constructor. Note that no cursor is needed when we create the
         * adapter. Instead, cursors are created on demand when completions are
         * needed for the field. (see
         * {@link ItemAutoTextAdapter#runQueryOnBackgroundThread(CharSequence)}.)
         *
         * @param dbHelper
         *            The AutoCompleteDbAdapter in use by the outer class
         *            object.
         */
        public ItemAutoTextAdapter(DBHelper dbHelper, String type) {
            // Call the CursorAdapter constructor with a null Cursor.
            super(AddTagNameActivity.this, null);
            mydb = dbHelper;
            typeOfField= type;
        }

        /**
         * Invoked by the AutoCompleteTextView field to get completions for the
         * current input.
         *
         * NOTE: If this method either throws an exception or returns null, the
         * Filter class that invokes it will log an error with the traceback,
         * but otherwise ignore the problem. No choice list will be displayed.
         * Watch those error logs!
         *
         * @param constraint
         *            The input entered thus far. The resulting query will
         *            search for states whose name begins with this string.
         * @return A Cursor that is positioned to the first row (if one exists)
         *         and managed by the activity.
         */
        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView tagNameAlert = findViewById(R.id.AddTagNameAlert);
                    tagNameAlert.setText("");
                    TextView tagRoomAlert = findViewById(R.id.AddTagRoomAlert);
                    tagRoomAlert.setText("");
                    TextView tagWorkplaceAlert = findViewById(R.id.AddTagWorkplaceAlert);
                    tagWorkplaceAlert.setText("");
                    System.out.println("flag2");
                }
            });
            if (getFilterQueryProvider() != null) {
                return getFilterQueryProvider().runQuery(constraint);
            }
            Cursor cursor = mydb.getMatchingStates((constraint != null ? constraint.toString() : null),typeOfField);
            return cursor;
        }

        /**
         Called by the AutoCompleteTextView field to get the text that will be
         entered in the field after a choice has been made.

         * @param
         *             cursor, positioned to a particular row in the list.
         * @return A String representing the row's text value. (Note that this
         *         specializes the base class return value for this method,
         *         which is {@link CharSequence}.)
         */
        @Override
        public String convertToString(Cursor cursor) {
            final int columnIndex = cursor.getColumnIndexOrThrow("name");
            final String str = cursor.getString(columnIndex);
            return str;
        }

        /**
         * Called by the ListView for the AutoCompleteTextView field to display
         * the text for a particular choice in the list.
         *
         * @param view
         *            The TextView used by the ListView to display a particular
         *            choice.
         * @param context
         *            The context (Activity) to which this form belongs;
         *            equivalent to {@code SelectState.this}.
         * @param cursor
         *            The cursor for the list of choices, positioned to a
         *            particular row.
         */
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final String text = convertToString(cursor);
            ((TextView) view).setText(text);
        }

        /**
         * Called by the AutoCompleteTextView field to display the text for a
         * particular choice in the list.
         *
         * @param context
         *            The context (Activity) to which this form belongs;
         *            equivalent to {@code SelectState.this}.
         * @param cursor
         *            The cursor for the list of choices, positioned to a
         *            particular row.
         * @param parent
         *            The ListView that contains the list of choices.
         *
         * @return A new View (really, a TextView) to hold a particular choice.
         */
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            final View view =
                    inflater.inflate(android.R.layout.simple_dropdown_item_1line,
                            parent, false);

            return view;
        }

        /**
         * Called by the AutoCompleteTextView field when a choice has been made
         * by the user.
         *
         * @param listView
         *            The ListView containing the choices that were displayed to
         *            the user.
         * @param view
         *            The field representing the selected choice
         * @param position
         *            The position of the choice within the list (0-based)
         * @param id
         *            The id of the row that was chosen (as provided by the _id
         *            column in the cursor.)
         */
        @Override
        public void onItemClick(AdapterView<?> listView, View view, int position, long id) {
            // Get the cursor, positioned to the corresponding row in the result set
            //Cursor cursor = (Cursor) listView.getItemAtPosition(position);

            // Get the state's capital from this row in the database.
            //String capital = cursor.getString(cursor.getColumnIndexOrThrow("name"));

        }
    }

}

