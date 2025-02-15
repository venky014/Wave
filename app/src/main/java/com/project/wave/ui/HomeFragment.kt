package com.project.wave.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.wave.R
import com.project.wave.databinding.FragmentHomeBinding
import com.project.wave.model.User

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupViewPager()
        setupClickListeners()
        loadUserAvatar()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = HomePagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Chats"
                1 -> "Users"
                else -> null
            }
        }.attach()
    }

    private fun setupClickListeners() {
        binding.profileAvatar.setOnClickListener {
            try {
                Log.d("HomeFragment", "Profile avatar clicked")
                val currentDestId = findNavController().currentDestination?.id
                Log.d("HomeFragment", "Current destination ID: $currentDestId")
                
                if (currentDestId == R.id.mainFragment) {
                    findNavController().navigate(R.id.action_to_profile)
                    Log.d("HomeFragment", "Navigation successful")
                } else {
                    Log.e("HomeFragment", "Invalid current destination for profile navigation")
                    Toast.makeText(context, "Navigation error: Invalid destination", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Navigation failed", e)
                Toast.makeText(
                    context,
                    "Navigation error: ${e.message}. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadUserAvatar() {
        val userId = auth.currentUser?.uid
        userId?.let { uid ->
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    val avatarId = document.getLong("avatarId")?.toInt() ?: 1
                    val avatarResId = resources.getIdentifier(
                        "avatar_$avatarId",
                        "drawable",
                        requireContext().packageName
                    )
                    binding.profileAvatar.setImageResource(avatarResId)
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class HomePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ChatListFragment()
                1 -> UsersFragment()
                else -> throw IllegalStateException("Invalid position $position")
            }
        }
    }
}