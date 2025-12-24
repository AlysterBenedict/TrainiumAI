package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aifitnesscoach.databinding.ActivityPhotoInstructionsBinding

class PhotoInstructionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoInstructionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoInstructionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startScanButton.setOnClickListener {
            // We will create CameraCaptureActivity in the next step
             val intent = Intent(this, CameraCaptureActivity::class.java)
             startActivity(intent)
        }
    }
}