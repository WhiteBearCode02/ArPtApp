package com.example.arptapp.ui.camera

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.example.arptapp.databinding.ActivityDashboardBinding

/** DEBUG-only test host. Inflates the real camera layout without authentication/session side effects.
 * Never starts a camera by itself, requests permissions, reads an account or stores frames.
 */
class CameraZoomVerificationActivity : ComponentActivity() {
    lateinit var binding: ActivityDashboardBinding
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only while this visible test Activity exists; never changes the device lock policy.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
