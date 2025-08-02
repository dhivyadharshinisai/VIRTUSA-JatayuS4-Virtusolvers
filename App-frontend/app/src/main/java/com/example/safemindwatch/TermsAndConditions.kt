package com.example.safemindwatch

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TermsAndConditions : AppCompatActivity() {

    private lateinit var tvEmail: TextView
    private lateinit var tvIntro: TextView
    private lateinit var btnAgree: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms_and_conditons)

        tvEmail = findViewById(R.id.userEmail)
        tvIntro = findViewById(R.id.tvIntro)
        btnAgree = findViewById(R.id.btnAgree)

        val email = intent.getStringExtra("email") ?: "example@gmail.com"
        val name = intent.getStringExtra("name")
        val password = intent.getStringExtra("password")
        val googleId = intent.getStringExtra("googleId")
        val signUpMethod = intent.getStringExtra("signUpMethod")
        tvEmail.text = email

        Log.d("T&C R", "name: $name")
        Log.d("T&C R", "email: $email")
        Log.d("T&C R", "password: $password")
        Log.d("T&C R", "googleId: $googleId")
        Log.d("T&C R", "signUpMethod: $signUpMethod")

        val para1 = "Safe Mind Watch is a cutting-edge parental digital safety application designed to protect minors in the online world. Our platform empowers parents and guardians to monitor their child’s internet usage, recognize signs of emotional distress or risky behavior, and receive timely alerts powered by advanced AI analysis."
        val para2 = "By using Safe Mind Watch, you agree to our "
        val terms = "Terms and Conditions"
        val mid = " and acknowledge our "
        val privacy = "Privacy Policies"
        val end = ". Please review them before proceeding."
        val fullText = "$para1\n\n$para2$terms$mid$privacy$end"

        val spannable = SpannableString(fullText)

        val indentPx = 50
        val para1Start = 0
        val para1End = para1.length
        val para2Start = fullText.indexOf(para2)
        val para2End = fullText.length
        spannable.setSpan(
            LeadingMarginSpan.Standard(indentPx, 0),
            para1Start, para1End,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            LeadingMarginSpan.Standard(indentPx, 0),
            para2Start, para2End,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val termsStart = fullText.indexOf(terms)
        val termsEnd = termsStart + terms.length
        val termsClickable = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(this@TermsAndConditions, TermsPrivacyActivity::class.java)
                intent.putExtra("type", "terms")
                startActivity(intent)
            }
        }
        spannable.setSpan(termsClickable, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(UnderlineSpan(), termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#1976D2")),
            termsStart, termsEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val privacyStart = fullText.indexOf(privacy)
        val privacyEnd = privacyStart + privacy.length
        val privacyClickable = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(this@TermsAndConditions, TermsPrivacyActivity::class.java)
                intent.putExtra("type", "privacy")
                startActivity(intent)
            }
        }
        spannable.setSpan(privacyClickable, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(UnderlineSpan(), privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#1976D2")),
            privacyStart, privacyEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tvIntro.text = spannable
        tvIntro.setLineSpacing(0f, 1.5f)
        tvIntro.movementMethod = LinkMovementMethod.getInstance()
        tvIntro.highlightColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tvIntro.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
        }

        btnAgree.setOnClickListener {
            val intent = Intent(this, ChildProfileCreation::class.java)
            intent.putExtra("name", name)
            intent.putExtra("email", email)
            intent.putExtra("password", password)
            intent.putExtra("googleId", googleId)
            intent.putExtra("signUpMethod", signUpMethod)
            startActivity(intent)
            finish()
        }
    }
}
