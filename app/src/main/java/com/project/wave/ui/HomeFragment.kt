package com.project.wave.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.wave.R
import com.project.wave.databinding.FragmentHomeBinding

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
                1 -> "Updates"
                else -> null
            }
        }.attach()
    }

    private fun setupClickListeners() {
        binding.profileAvatar.setOnClickListener {
            findNavController().navigate(R.id.action_to_profile)
        }

        binding.searchFab.setOnClickListener {
            findNavController().navigate(R.id.action_to_search)
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
                1 -> UpdatesFragment()
                else -> throw IllegalStateException("Invalid position $position")
            }
        }
    }
} 