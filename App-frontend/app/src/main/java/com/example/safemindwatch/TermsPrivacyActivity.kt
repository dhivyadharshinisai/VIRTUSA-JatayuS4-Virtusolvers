package com.example.safemindwatch

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TermsPrivacyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms_privacy)

        val textView = findViewById<TextView>(R.id.tvPolicyContent)
        val type = intent.getStringExtra("type")

        if (type == "terms") {
            title = "Terms & Conditions"
            textView.text = getString(R.string.terms_text)
        } else {
            title = "Privacy Policy"
            textView.text = getString(R.string.privacy_text)
        }
    }
}
