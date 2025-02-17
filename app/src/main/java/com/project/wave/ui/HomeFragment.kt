package com.project.wave.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.project.wave.R
import com.project.wave.databinding.FragmentHomeBinding
import com.project.wave.model.ChatItem
import com.project.wave.ui.adapter.ChatListAdapter

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var chatAdapter: ChatListAdapter

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

        setupProfileAvatar()
        setupSearchBarClick()
        setupChatList()
    }

    private fun setupSearchBarClick() {
        binding.searchInput.setOnClickListener {
            findNavController().navigate(R.id.action_to_search)
        }
        // Make the EditText not focusable to only handle clicks
        binding.searchInput.isFocusable = false
        binding.searchInput.isFocusableInTouchMode = false
    }

    private fun updateVisibility(chatItems: List<ChatItem>) {
        binding.apply {
            if (chatItems.isEmpty()) {
                chatList.visibility = View.GONE
                noChatsText.visibility = View.VISIBLE
            } else {
                chatList.visibility = View.VISIBLE
                noChatsText.visibility = View.GONE
            }
        }
    }

    private fun setupProfileAvatar() {
        binding.profileAvatar.setOnClickListener {
            findNavController().navigate(R.id.action_to_profile)
        }
        
        // Load user's avatar
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
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

    private fun setupChatList() {
        chatAdapter = ChatListAdapter(
            currentUserId = auth.currentUser?.uid,
            onChatClick = { chatItem ->
                val action = HomeFragmentDirections.actionToChat(
                    userId = if (chatItem.senderId == auth.currentUser?.uid) 
                        chatItem.receiverId else chatItem.senderId,
                    userRollNumber = chatItem.otherUserRollNumber ?: "",
                    userAvatarId = chatItem.otherUserAvatarId,
                    userEmail = ""
                )
                findNavController().navigate(action)
            }
        )

        binding.chatList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        loadChats()
    }

    private fun loadChats() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("HomeFragment", "Error loading chats", e)
                    return@addSnapshotListener
                }

                if (snapshots?.isEmpty == true) {
                    updateVisibility(emptyList())
                    return@addSnapshotListener
                }

                val chatItems = mutableListOf<ChatItem>()
                val totalChats = snapshots?.documents?.size ?: 0
                var processedChats = 0
                
                snapshots?.documents?.forEach { doc ->
                    try {
                        val otherUserId = (doc.get("participants") as? List<String>)?.find { it != currentUserId }
                        
                        if (otherUserId != null) {
                            db.collection("chats")
                                .document(doc.id)
                                .collection("messages")
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .limit(1)
                                .get()
                                .addOnSuccessListener { messageSnapshot ->
                                    val lastMessage = messageSnapshot.documents.firstOrNull()
                                    
                                    db.collection("users")
                                        .document(otherUserId)
                                        .get()
                                        .addOnSuccessListener { userDoc ->
                                            val chatItem = ChatItem(
                                                id = doc.id,
                                                lastMessage = lastMessage?.getString("text") ?: "",
                                                timestamp = lastMessage?.getLong("timestamp") ?: 0,
                                                participants = doc.get("participants") as? List<String> ?: listOf(),
                                                status = doc.getString("status") ?: "pending",
                                                senderId = lastMessage?.getString("senderId") ?: "",
                                                receiverId = doc.getString("receiverId") ?: "",
                                                otherUserRollNumber = userDoc.getString("rollNumber"),
                                                otherUserAvatarId = userDoc.getLong("avatarId")?.toInt() ?: 1
                                            )
                                            chatItems.add(chatItem)
                                            
                                            processedChats++
                                            if (processedChats == totalChats) {
                                                val sortedItems = chatItems.sortedByDescending { it.timestamp }
                                                chatAdapter.submitList(sortedItems)
                                                updateVisibility(sortedItems)
                                            }
                                        }
                                }
                        }
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Error mapping chat", e)
                        processedChats++
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}