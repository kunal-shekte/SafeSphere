package com.example.safesphere;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    private EditText etName, etPhone, etKeyword, etE1, etE2, etE3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etKeyword = findViewById(R.id.etKeyword);
        etE1 = findViewById(R.id.etE1);
        etE2 = findViewById(R.id.etE2);
        etE3 = findViewById(R.id.etE3);
        Button btnSave = findViewById(R.id.btnSave);

        SharedPreferences sp = Prefs.getAll(this);
        etName.setText(sp.getString("name", ""));
        etPhone.setText(sp.getString("phone", ""));
        etKeyword.setText(sp.getString("keyword", ""));
        etE1.setText(sp.getString("e1", ""));
        etE2.setText(sp.getString("e2", ""));
        etE3.setText(sp.getString("e3", ""));

        btnSave.setOnClickListener(v -> {
            Prefs.saveUser(
                    ProfileActivity.this,
                    etName.getText().toString().trim(),
                    etPhone.getText().toString().trim(),
                    etKeyword.getText().toString().trim(),
                    etE1.getText().toString().trim(),
                    etE2.getText().toString().trim(),
                    etE3.getText().toString().trim()
            );
            Toast.makeText(ProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
