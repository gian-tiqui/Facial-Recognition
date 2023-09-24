package com.example.mlseriesdemonstrator.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import com.example.mlseriesdemonstrator.R;
import com.example.mlseriesdemonstrator.model.User;
import com.example.mlseriesdemonstrator.utilities.Utility;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import okhttp3.internal.Util;

public class EditNameActivity extends AppCompatActivity {

    private EditText firstNameTxt;
    private EditText middleNameTxt;
    private EditText lastNameTxt;
    private Button editDetailsBtn;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_name);

        user = Utility.getUser();
        editDetailsBtn = findViewById(R.id.EDIT_DETAILS_BTN);
        firstNameTxt = findViewById(R.id.FIRST_NAME_TXT);
        middleNameTxt = findViewById(R.id.MIDDLE_NAME_TXT);
        lastNameTxt = findViewById(R.id.LAST_NAME_TXT);

        firstNameTxt.setText(user.getFirstName());
        middleNameTxt.setText(user.getMiddleName());
        lastNameTxt.setText(user.getLastName());

        editDetailsBtn.setOnClickListener(v -> editDone());
    }

    private void editDone() {

        String firstNameStr = firstNameTxt.getText().toString();
        String middleNameStr = middleNameTxt.getText().toString();
        String lastNameStr = lastNameTxt.getText().toString();

        if (firstNameStr.isEmpty()) {
            firstNameTxt.setError("First name required");
        }

        if (middleNameStr.isEmpty()) {
            firstNameTxt.setError("Middle name required");
        }

        if (lastNameStr.isEmpty()) {
            firstNameTxt.setError("Last name required");
        }

        Intent intent = new Intent(EditNameActivity.this, ConfirmActivity.class);
        intent.putExtra("first_name", firstNameStr);
        intent.putExtra("middle_name", middleNameStr);
        intent.putExtra("last_name", lastNameStr);

        startActivity(intent);
        finish();
    }
}