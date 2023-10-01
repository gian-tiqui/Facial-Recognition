package com.example.mlseriesdemonstrator.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mlseriesdemonstrator.R;
import com.example.mlseriesdemonstrator.utilities.Activation;
import com.example.mlseriesdemonstrator.utilities.Utility;
import com.google.firebase.auth.FirebaseAuth;

import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public class SignInActivity extends AppCompatActivity {

    final String mode = "login";
    Context context;
    EditText inputTxt;
    EditText passwordTxt;
    TextView forgotPasswordTxt;
    Button signInBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        // Make the buttons interactive
        inputTxt = findViewById(R.id.INPUT_TXT);
        passwordTxt = findViewById(R.id.PASSWORD_TXT);
        forgotPasswordTxt = findViewById(R.id.FORGOT_PASSWORD_TXT);
        signInBtn = findViewById(R.id.SIGN_IN_BTN);

        // Set the content of the screen
        context = SignInActivity.this;

        // When the button is pressed, perform action
        signInBtn.setOnClickListener(v -> {
            try {
                loginAccount();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private boolean isEmail(String input) {
        return Patterns.EMAIL_ADDRESS.matcher(input).matches();
    }

    private boolean validateData(String email) {
        if (email == null) {
            inputTxt.setError("Email is empty.");
            return false;
        }

        // Check if the email input is correct
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputTxt.setError("Email is invalid.");
            return false;
        }

        return true;
    }

    private void loginAccountFirebase(String email, String password) {

        // Check if user exists and if not, display errors in the screen below
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                   if (task.isSuccessful()) {
                       if (Objects.requireNonNull(firebaseAuth.getCurrentUser()).isEmailVerified()) {
                           startActivity(new Intent(context, LoadingActivity.class));
                           finish();
                       } else {
                           Utility.showToast(context, "Email is not verified.");
                       }
                   } else {
                       Utility.showToast(context, Objects.requireNonNull(task
                               .getException())
                               .getLocalizedMessage()
                       );
                   }
                });
    }

    private void loginAccount() throws NoSuchAlgorithmException {
        // Get texts from the user input
        String password = passwordTxt.getText().toString();
        String input = inputTxt.getText().toString();

        if (!isEmail(input)) {
            retrieveEmail(input, email -> {
                if (email != null) {
                    loginAccountFirebase(email, password);
                } else {
                    Utility.showToast(context, "No email found for this id.");
                }
            });
        } else {
            // The input is already an email, proceed with login
            loginAccountFirebase(input, password);
        }
    }

    private void retrieveEmail(String input, final EmailCallback callback) {
        retrieveStudentAndEmployee(input, email -> {
            if (email != null) {
                callback.onEmailRetrieved(email);
            } else {
                callback.onEmailRetrieved(null);
            }
        });
    }

    private void retrieveStudentAndEmployee(String input, final EmailCallback callback) {
        Activation.getStudentById(input, context, mode, student -> {
            if (student != null) {
                callback.onEmailRetrieved(student.getInstitutionalEmail());
            } else {
                retrieveEmployee(input, callback);
            }
        });
    }

    private void retrieveEmployee(String input, final EmailCallback callback) {
        Activation.getEmployeeById(input, context, mode, employee -> {
            if (employee != null) {
                callback.onEmailRetrieved(employee.getInstitutionalEmail());
            } else {
                callback.onEmailRetrieved(null);
            }
        });
    }

    interface EmailCallback {
        void onEmailRetrieved(String email);
    }



}