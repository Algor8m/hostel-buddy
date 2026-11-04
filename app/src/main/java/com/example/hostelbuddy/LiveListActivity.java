package com.example.hostelbuddy;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;



public abstract class LiveListActivity extends AppCompatActivity {
    protected TextView contentText, titleText, subtitleText;
    protected DatabaseReference reference;

    protected void setupLiveList(String title, String subtitle, String path, String emptyMessage) {
        setContentView(R.layout.activity_list);
        titleText = findViewById(R.id.pageTitle);
        subtitleText = findViewById(R.id.pageSubtitle);
        contentText = findViewById(R.id.contentText);
        titleText.setText(title);
        subtitleText.setText(subtitle);
        contentText.setText("Connecting to live updates…");
        findViewById(R.id.backButton).setOnClickListener(v ->
            startActivity(new Intent(this, DashboardActivity.class)));
        reference = DatabaseConfig.getDatabase().getReference(path);
        reference.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) { contentText.setText(emptyMessage); return; }
                StringBuilder out = new StringBuilder();
                renderSnapshot(snapshot, out);
                contentText.setText(out.length() == 0 ? emptyMessage : out.toString());
            }
            @Override public void onCancelled(DatabaseError error) {
                contentText.setText("Could not load live data: " + error.getMessage()
                    + "\n\nCheck your Firebase configuration and database rules.");
            }
        });
    }

    protected abstract void renderSnapshot(DataSnapshot snapshot, StringBuilder out);
}
