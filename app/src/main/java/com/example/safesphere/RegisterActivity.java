package com.example.safesphere;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        EditText etName = findViewById(R.id.etRegName);
        EditText etPhone = findViewById(R.id.etRegPhone);
        EditText etKeyword = findViewById(R.id.etRegKeyword);
        EditText etE1 = findViewById(R.id.etRegE1);
        EditText etE2 = findViewById(R.id.etRegE2);
        EditText etE3 = findViewById(R.id.etRegE3);
        Button btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String keyword = etKeyword.getText().toString().trim();
            String e1 = etE1.getText().toString().trim();
            String e2 = etE2.getText().toString().trim();
            String e3 = etE3.getText().toString().trim();

            if (name.isEmpty() || phone.isEmpty() || keyword.isEmpty()) {
                Toast.makeText(this, "Fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            Prefs.saveUser(this, name, phone, keyword, e1, e2, e3);
            Toast.makeText(this, "Registered", Toast.LENGTH_SHORT).show();

            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
            finish();
        });
    }
}
