package com.project.wave.ui
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.wave.R
import com.project.wave.databinding.FragmentProfileBinding


class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var selectedAvatarId: Int = 1

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupAvatarSelection()
        loadUserData()
        setupLogout()
    }

    private fun setupAvatarSelection() {
        binding.avatarContainer.children.forEachIndexed { index, view ->
            view.setOnClickListener {
                selectedAvatarId = index + 1
                updateSelectedAvatar()
                updateUserAvatar()
            }
        }
    }

    private fun updateUserAvatar() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .update("avatarId", selectedAvatarId)
    }

    private fun updateSelectedAvatar() {
        binding.avatarContainer.children.forEachIndexed { index, view ->
            view.alpha = if (index + 1 == selectedAvatarId) 1.0f else 0.5f
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                selectedAvatarId = document.getLong("avatarId")?.toInt() ?: 1
                updateSelectedAvatar()
                
                binding.emailText.text = auth.currentUser?.email
                binding.rollNumberText.text = document.getString("rollNumber")
            }
    }

    private fun setupLogout() {
        binding.logoutButton.setOnClickListener {
            auth.signOut()
            findNavController().navigate(R.id.action_to_getStarted)
        }
    }
} 