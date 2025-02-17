package com.project.wave.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.wave.R
import com.project.wave.databinding.FragmentUsersBinding
import com.project.wave.model.User
import com.project.wave.ui.adapter.SearchAdapter

class SearchFragment : Fragment() {
    private var _binding: FragmentUsersBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userAdapter: SearchAdapter
    private var allUsers = listOf<User>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        setupSearch()
        loadUsers()
    }

    private fun setupRecyclerView() {
        userAdapter = SearchAdapter { user ->
            val action = SearchFragmentDirections.actionToChat(
                userId = user.id,
                userEmail = user.email,
                userRollNumber = user.rollNumber,
                userAvatarId = user.avatarId
            )
            findNavController().navigate(action)
        }
        
        binding.usersList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = userAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener { text ->
            filterUsers(text?.toString() ?: "")
        }
    }

    private fun loadUsers() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        db.collection("users")
            .get()
            .addOnSuccessListener { documents ->
                // Filter out current user and map to User objects
                allUsers = documents.mapNotNull { doc ->
                    // Only create User object if it's not the current user
                    if (doc.id != currentUserId) {
                        User(
                            id = doc.id,
                            email = doc.getString("email") ?: "",
                            rollNumber = doc.getString("rollNumber") ?: "",
                            avatarId = doc.getLong("avatarId")?.toInt() ?: 1
                        )
                    } else null
                }.sortedBy { it.rollNumber } // Sort by roll number
                
                userAdapter.submitList(allUsers)
                updateVisibility()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context, 
                    "Error loading users: ${e.message}", 
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun filterUsers(query: String) {
        val filteredUsers = if (query.isEmpty()) {
            allUsers
        } else {
            allUsers.filter { user ->
                user.rollNumber.lowercase().contains(query.lowercase()) ||
                user.email.lowercase().contains(query.lowercase())
            }
        }
        userAdapter.submitList(filteredUsers)
        updateVisibility()
    }

    private fun updateVisibility() {
        binding.usersList.visibility = if (allUsers.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 