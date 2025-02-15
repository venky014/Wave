package com.project.wave.ui
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.wave.R
import com.project.wave.databinding.FragmentProfileBinding
import de.hdodenhof.circleimageview.CircleImageView


class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupBackButton()
        setupAvatarEdit()
        loadUserData()
        setupLogout()
    }

    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupAvatarEdit() {
        binding.editAvatarButton.setOnClickListener {
            showAvatarSelectionDialog()
        }
    }

    private fun showAvatarSelectionDialog() {
        val dialog = Dialog(requireContext())
        val gridLayout = GridLayout(requireContext()).apply {
            columnCount = 4
            rowCount = 2
            setPadding(32, 32, 32, 32)
        }

        // Add avatar options to grid
        for (i in 1..8) {
            val avatarView = CircleImageView(requireContext()).apply {
                val size = resources.getDimensionPixelSize(R.dimen.avatar_size)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(8, 8, 8, 8)
                }
                val avatarResId = resources.getIdentifier(
                    "avatar_$i",
                    "drawable",
                    requireContext().packageName
                )
                setImageResource(avatarResId)
                setOnClickListener {
                    updateAvatar(i)
                    dialog.dismiss()
                }
            }
            gridLayout.addView(avatarView)
        }

        dialog.setContentView(gridLayout)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    private fun updateAvatar(avatarId: Int) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .update("avatarId", avatarId)
            .addOnSuccessListener {
                // Update local avatar
                val avatarResId = resources.getIdentifier(
                    "avatar_$avatarId",
                    "drawable",
                    requireContext().packageName
                )
                binding.profileAvatar.setImageResource(avatarResId)
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to update avatar", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                // Set avatar
                val avatarId = document.getLong("avatarId")?.toInt() ?: 1
                val avatarResId = resources.getIdentifier(
                    "avatar_$avatarId",
                    "drawable",
                    requireContext().packageName
                )
                binding.profileAvatar.setImageResource(avatarResId)
                
                // Set user info
                binding.emailText.text = auth.currentUser?.email
                binding.rollNumberText.text = "Roll Number: ${document.getString("rollNumber")}"
            }
    }

    private fun setupLogout() {
        binding.logoutButton.setOnClickListener {
            auth.signOut()
            findNavController().navigate(R.id.action_to_getStarted)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 