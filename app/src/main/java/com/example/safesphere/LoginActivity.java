package com.example.safesphere;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText etLoginPhone = findViewById(R.id.etLoginPhone);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnGoRegister = findViewById(R.id.btnGoRegister);

        // 🔐 LOGIN BUTTON
        btnLogin.setOnClickListener(v -> {

            String entered = etLoginPhone.getText().toString().trim();
            String saved   = Prefs.getUserPhone(this);

            // ❗ Agar register nahi kiya
            if (saved == null || saved.isEmpty()) {
                Toast.makeText(this, "Please register first", Toast.LENGTH_SHORT).show();
                return;
            }

            // ❗ Phone mismatch
            if (!entered.equals(saved)) {
                Toast.makeText(this, "Invalid phone number", Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ Login success
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // 🔁 Go to Register screen
        btnGoRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }
}
