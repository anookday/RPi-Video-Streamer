package com.anookday.rpistream.landing

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.ActivityLandingBinding
import com.anookday.rpistream.login.LoginActivity
import com.anookday.rpistream.stream.StreamActivity
import kotlinx.android.synthetic.main.nav_header.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

/**
 * Activity that displays app's splash screen and navigates based on user authentication.
 */
class LandingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLandingBinding
    private val viewModel by viewModels<LandingViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_landing)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        viewModel.user.observe(this, Observer { user ->
            if (user != null) {
                if (System.currentTimeMillis() > user.auth.tokenExpiryDate) {
                    viewModel.logout()
                } else {
                    val streamIntent = Intent(this, StreamActivity::class.java)
                    streamIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(streamIntent)
                    finish()
                }
            } else {
                lifecycleScope.launch {
                    delay(1000)
                    startActivity(Intent(this@LandingActivity, LoginActivity::class.java))
                    finish()
                }
            }
        })
    }
}