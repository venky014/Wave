package com.project.wave.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.project.wave.databinding.FragmentChatBinding
import com.project.wave.model.ChatPermission
import com.project.wave.model.Message
import com.project.wave.model.MessageType
import com.project.wave.model.PermissionStatus
import com.project.wave.model.User
import com.project.wave.ui.adapter.ChatAdapter
import java.util.UUID
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var adapter: ChatAdapter
    
    private val args: ChatFragmentArgs by navArgs()
    private var otherUserId: String? = null
    private var otherUser: User? = null
    private var isFirstMessage = true
    private var isAllowed = false
    private var permissionStatus: PermissionStatus = PermissionStatus.PENDING
    private val initialMessages = listOf("Hi", "Hello", "I need your help!")
    private val STORAGE_PERMISSION_CODE = 1
    private var isMessageAccepted = false

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadFile(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        
        // Get user data from arguments
        otherUserId = args.userId
        otherUser = User(
            id = args.userId,
            email = args.userEmail,
            rollNumber = args.userRollNumber,
            avatarId = args.userAvatarId
        )
        
        setupUI()
        setupRecyclerView()
        loadChatStatus()
        loadMessages()
    }

    private fun setupUI() {
        // Hide normal message input initially
        binding.messageInputLayout.visibility = View.GONE
        
        // Show preset messages for sender if it's first message
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != args.userId) {
            binding.presetMessagesLayout.visibility = View.VISIBLE
            binding.messageInputLayout.visibility = View.GONE
            setupPresetMessages()
        } else {
            binding.presetMessagesLayout.visibility = View.GONE
            showAcceptDialog()
        }
        
        updateToolbarInfo()
    }

    private fun showAcceptDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Message Request")
            .setMessage("Would you like to accept messages from this user?")
            .setPositiveButton("Accept") { _, _ ->
                acceptChat()
            }
            .setNegativeButton("Block") { _, _ ->
                blockChat()
            }
            .setCancelable(false)
            .show()
    }

    private fun acceptChat() {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, args.userId)
        
        db.collection("chats").document(chatId)
            .update("status", "accepted")
            .addOnSuccessListener {
                isMessageAccepted = true
                binding.messageInputLayout.visibility = View.VISIBLE
                binding.presetMessagesLayout.visibility = View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to accept chat", Toast.LENGTH_SHORT).show()
            }
    }

    private fun blockChat() {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, args.userId)
        
        db.collection("chats").document(chatId)
            .update("status", "blocked")
            .addOnSuccessListener {
                findNavController().navigateUp()
            }
    }

    private fun loadChatStatus() {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, args.userId)
        
        db.collection("chats").document(chatId)
            .get()
            .addOnSuccessListener { document ->
                isMessageAccepted = document.getString("status") == "accepted"
                updateUIBasedOnStatus()
            }
    }

    private fun updateUIBasedOnStatus() {
        binding.apply {
            if (isMessageAccepted) {
                messageInputLayout.visibility = View.VISIBLE
                presetMessagesLayout.visibility = View.GONE
            } else {
                val currentUserId = auth.currentUser?.uid
                if (currentUserId != args.userId) {
                    // Sender view
                    messageInputLayout.visibility = View.GONE
                    presetMessagesLayout.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun sendFirstMessage(message: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, args.userId)
        
        // Create chat document first
        db.collection("chats").document(chatId)
            .set(mapOf(
                "participants" to listOf(currentUserId, args.userId),
                "status" to "pending",
                "senderId" to currentUserId,
                "receiverId" to args.userId,
                "createdAt" to System.currentTimeMillis()
            ))
            .addOnSuccessListener {
                // Then send the message
                val message = Message(
                    id = UUID.randomUUID().toString(),
                    senderId = currentUserId,
                    receiverId = args.userId,
                    text = message,
                    timestamp = System.currentTimeMillis(),
                    type = MessageType.TEXT
                )
                
                db.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .add(message)
                    .addOnSuccessListener {
                        binding.presetMessagesLayout.visibility = View.GONE
                        Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to create chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateToolbarInfo() {
        with(binding) {
            // Set user info from arguments
            userRollNumber.text = otherUser?.rollNumber
            userEmail.text = otherUser?.email
            
            // Set avatar
            val avatarResId = resources.getIdentifier(
                "avatar_${otherUser?.avatarId ?: 1}",
                "drawable",
                requireContext().packageName
            )
            userAvatar.setImageResource(avatarResId)
            
            // Setup back button
            backButton.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }

    private fun setupPresetMessages() {
        binding.apply {
            presetMessage1Button.setOnClickListener { 
                sendFirstMessage("Hi")
            }
            
            presetMessage2Button.setOnClickListener {
                sendFirstMessage("Hello")
            }
            
            presetMessage3Button.setOnClickListener {
                sendFirstMessage("I need your help!")
            }
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    STORAGE_PERMISSION_CODE
                )
            } else {
                openFilePicker()
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            } else {
                openFilePicker()
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
        }
        filePickerLauncher.launch(intent)
    }

    private fun uploadFile(uri: Uri) {
        val fileName = UUID.randomUUID().toString()
        val fileRef = storage.reference.child("chat_files/$fileName")
        
        fileRef.putFile(uri)
            .addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val message = Message(
                        id = UUID.randomUUID().toString(),
                        senderId = auth.currentUser?.uid ?: return@addOnSuccessListener,
                        receiverId = otherUserId ?: return@addOnSuccessListener,
                        text = fileName,
                        fileUrl = downloadUrl.toString(),
                        type = MessageType.FILE,
                        timestamp = System.currentTimeMillis()
                    )
                    sendMessage(message.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to upload file", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openFile(fileUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(fileUrl)
        }
        startActivity(intent)
    }

    private fun sendMessage(text: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        otherUserId?.let { recipientId ->
            val chatId = getChatId(currentUserId, recipientId)
            
            val message = Message(
                id = UUID.randomUUID().toString(),
                senderId = currentUserId,
                receiverId = recipientId,
                text = text,
                timestamp = System.currentTimeMillis(),
                type = MessageType.TEXT,
                allowed = isAllowed
            )
            
                    db.collection("chats")
                        .document(chatId)
                        .collection("messages")
                        .add(message)
                        .addOnSuccessListener {
                    // Clear input after sending
                    binding.messageInput.setText("")
                }
        }
    }

    private fun getChatId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    private fun loadMessages() {
        val currentUserId = auth.currentUser?.uid ?: return
        otherUserId?.let { recipientId ->
            val chatId = getChatId(currentUserId, recipientId)
            
            // Listen for real-time updates
            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Toast.makeText(context, "Error loading messages", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    val messages = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(Message::class.java)
                    } ?: emptyList()

                    adapter.submitList(messages)
                    
                    // Scroll to bottom when new message arrives
                    if (messages.isNotEmpty()) {
                        binding.messagesRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }
        }
    }

    private fun setupRecyclerView() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        adapter = ChatAdapter(
            currentUserId = currentUserId,
            onFileClick = { fileUrl ->
                // Handle file click if needed
            }
        )

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true  // Messages stack from bottom
            }
            adapter = this@ChatFragment.adapter
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openFilePicker()
                } else {
                    Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 