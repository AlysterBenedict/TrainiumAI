package com.example.aifitnesscoach

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.aifitnesscoach.databinding.FragmentProfileBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isBiometricEnabled = sharedPrefs.getBoolean("biometric_enabled", false)
        binding.biometricSwitch.isChecked = isBiometricEnabled

        val user = Firebase.auth.currentUser
        user?.let {
            binding.nameTextView.text = it.displayName
            Glide.with(this).load(it.photoUrl).circleCrop().into(binding.profileImageView)
        }

        binding.biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Check if device can even support biometrics before enabling
            val biometricManager = BiometricManager.from(requireContext())
            if (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
                Toast.makeText(requireContext(), "Biometric authentication is not available on this device.", Toast.LENGTH_LONG).show()
                binding.biometricSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }

            // Save the user's choice
            sharedPrefs.edit().putBoolean("biometric_enabled", isChecked).apply()
            if (isChecked) {
                Toast.makeText(requireContext(), "Biometric lock enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Biometric lock disabled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.logoutButton.setOnBounceClickListener { buttonView ->
            buttonView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            Firebase.auth.signOut()
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}