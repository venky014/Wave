package com.project.wave.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
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
import com.google.firebase.firestore.SetOptions
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
import com.google.firebase.firestore.ListenerRegistration

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ChatAdapter
    private var messageListener: ListenerRegistration? = null
    private var messages = mutableListOf<Message>()
    private val currentUserId get() = auth.currentUser?.uid
    private val chatId get() = getChatId(currentUserId ?: "", args.userId)
    
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
        
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        otherUserId = args.userId
        setupToolbar()
        setupRecyclerView()
        setupMessageInput()
        loadMessages()
        setupAttachmentButton()

        // Check if we're the receiver and there's a "Hi" message
        checkForAcceptanceDialog()
    }

    private fun setupToolbar() {
        db.collection("users").document(args.userId).get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val rollNumber = document.getString("rollNumber") ?: ""
                    val avatarId = document.getLong("avatarId")?.toInt() ?: 1
                    
                    binding.apply {
                        toolbar.title = rollNumber.ifEmpty { "User" }
                        
                        // Set user avatar in toolbar
            val avatarResId = resources.getIdentifier(
                            "avatar_$avatarId",
                "drawable",
                requireContext().packageName
            )
            userAvatar.setImageResource(avatarResId)
            
            // Setup back button
                        toolbar.setNavigationOnClickListener {
                findNavController().navigateUp()
            }
        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatFragment", "Error loading user details", e)
                binding.toolbar.title = "User"
        }
    }

    private fun setupMessageInput() {
        // Initially disable message input for receiver until accepted
        if (currentUserId != args.userId) {
            binding.messageInput.isEnabled = false
            binding.sendButton.isEnabled = false
        }

        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString().trim()
            
            if (messages.isEmpty() && currentUserId == args.userId) {
                if (message.equals("Hi", ignoreCase = true)) {
                    sendFirstMessage(message)
                } else {
                    showInitialMessageDialog()
                }
            } else {
                checkAndSendMessage(message)
            }
        }

        // Check chat status on start
        checkChatStatus()
    }

    private fun checkChatStatus() {
        db.collection("chats")
            .document(chatId)
            .get()
            .addOnSuccessListener { document ->
                val status = document.getString("status") ?: "pending"
                val isAccepted = document.getBoolean("isAccepted") ?: false

                if (status == "accepted" || isAccepted) {
                    binding.messageInput.isEnabled = true
                    binding.sendButton.isEnabled = true
                    isAllowed = true
                }
            }
    }

    private fun checkAndSendMessage(message: String) {
        if (currentUserId == null) return

        if (isAllowed) {
            sendMessage(message)
        } else {
            db.collection("chats")
                .document(chatId)
                .get()
                .addOnSuccessListener { document ->
                    val status = document.getString("status") ?: "pending"
                    val senderId = document.getString("senderId")
                    val isAccepted = document.getBoolean("isAccepted") ?: false

                    when {
                        // Sender can only send "Hi" when status is pending
                        status == "pending" && currentUserId == senderId -> {
                            showWaitForAcceptanceDialog()
                        }
                        // Anyone can send messages when status is accepted
                        status == "accepted" || isAccepted -> {
                            sendMessage(message)
                            isAllowed = true
                            binding.messageInput.isEnabled = true
                            binding.sendButton.isEnabled = true
                        }
                        // No messages allowed when blocked
                        status == "blocked" -> {
                            Toast.makeText(context, "This chat is blocked", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
    }

    private fun showWaitForAcceptanceDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("Wait for the User acceptance")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun sendFirstMessage(message: String) {
        if (currentUserId == null) return
        
        // First create the chat document
        val chatData = hashMapOf(
            "senderId" to currentUserId,
            "receiverId" to args.userId,
            "lastMessage" to message,
            "timestamp" to System.currentTimeMillis(),
            "status" to "pending",
            "participants" to listOf(currentUserId, args.userId)
        )

        db.collection("chats")
            .document(chatId)
            .set(chatData)
            .addOnSuccessListener {
                // Then add the first message
                val messageData = hashMapOf(
                    "senderId" to currentUserId,
                    "text" to message,
                    "timestamp" to System.currentTimeMillis(),
                    "type" to MessageType.TEXT.name
                )

                db.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .add(messageData)
                    .addOnSuccessListener {
                        binding.messageInput.text.clear()
                    }
            }
    }

    private fun showInitialMessageDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("First message must be 'Hi'")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun sendMessage(message: String) {
        if (message.isEmpty() || currentUserId == null) return
        
        val messageData = hashMapOf(
            "senderId" to currentUserId,
            "text" to message,
            "timestamp" to System.currentTimeMillis(),
            "type" to MessageType.TEXT.name,
            "isFirstMessage" to isFirstMessage
        )

        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(messageData)
            .addOnSuccessListener {
                binding.messageInput.text.clear()
                updateLastMessage(chatId, message)
                if (isFirstMessage) isFirstMessage = false
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error sending message: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateLastMessage(chatId: String, message: String) {
        db.collection("chats")
            .document(chatId)
            .update(
                mapOf(
                    "lastMessage" to message,
                    "timestamp" to System.currentTimeMillis()
                )
            )
    }

    private fun loadMessages() {
        val currentUserId = auth.currentUser?.uid ?: return
        otherUserId?.let { recipientId ->
            val chatId = getChatId(currentUserId, recipientId)
            
            // Remove previous listener if exists
            messageListener?.remove()
            
            messageListener = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("ChatFragment", "Error loading messages", error)
                        return@addSnapshotListener
                    }

                    if (_binding == null) return@addSnapshotListener

                    val messages = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            Message(
                                id = doc.id,
                                senderId = doc.getString("senderId") ?: "",
                                text = doc.getString("text") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0,
                                type = MessageType.fromString(doc.getString("type") ?: "TEXT"),
                                isAccepted = doc.getBoolean("isAccepted") ?: false,
                                isFirstMessage = doc.getBoolean("isFirstMessage") ?: false
                            )
                        } catch (e: Exception) {
                            Log.e("ChatFragment", "Error converting message", e)
                            null
                        }
                    } ?: emptyList()

                    adapter.submitList(messages)
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

    private fun getChatId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        messageListener?.remove() // Clean up listener
        _binding = null
    }

    companion object {
        private const val FILE_PICK_CODE = 100
    }

    private fun setupAttachmentButton() {
        binding.attachButton.setOnClickListener {
            if (checkStoragePermission()) {
                openFilePicker()
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
                return false
            }
        }
        return true
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }

    private fun uploadFile(uri: Uri) {
        val fileName = getFileName(uri) ?: "file"
        binding.progressBar.visibility = View.VISIBLE

        val storageRef = FirebaseStorage.getInstance().reference
            .child("chat_files")
            .child(chatId)
            .child("${UUID.randomUUID()}_$fileName")

        storageRef.putFile(uri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                    sendFileMessage(downloadUrl.toString(), fileName)
                    binding.progressBar.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Error uploading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        context?.contentResolver?.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            fileName = cursor.getString(nameIndex)
        }
        return fileName
    }

    private fun sendFileMessage(fileUrl: String, fileName: String) {
        if (currentUserId == null) return
        
        val messageData = hashMapOf(
            "senderId" to currentUserId,
            "message" to fileName,
            "fileUrl" to fileUrl,
            "timestamp" to System.currentTimeMillis(),
            "type" to MessageType.FILE.name,
            "read" to false
        )

        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(messageData)
            .addOnSuccessListener {
                updateLastMessage(chatId, "Sent a file: $fileName")
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error sending file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAcceptanceDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Chat Request")
            .setMessage("Allow this user to send you messages?")
            .setPositiveButton("Allow") { _, _ ->
                updateChatAcceptance(true)
            }
            .setNegativeButton("Block") { _, _ ->
                updateChatAcceptance(false)
                findNavController().navigateUp()
            }
            .setCancelable(false)
            .show()
    }

    private fun updateChatAcceptance(accepted: Boolean) {
        val newStatus = if (accepted) "accepted" else "blocked"
        
        db.collection("chats")
            .document(chatId)
            .update(
                mapOf(
                    "status" to newStatus,
                    "isAccepted" to accepted  // Add this field
                )
            )
            .addOnSuccessListener {
                if (accepted) {
                    // Enable message input after acceptance
                    binding.messageInput.isEnabled = true
                    binding.sendButton.isEnabled = true
                    isAllowed = true  // Set permission flag
                } else {
                    findNavController().navigateUp()
                }
            }
    }

    private fun checkForAcceptanceDialog() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        // First check if a chat already exists
        db.collection("chats")
            .document(chatId)
            .get()
            .addOnSuccessListener { chatDoc ->
                if (chatDoc.exists()) {
                    // Chat exists, check status
                    val status = chatDoc.getString("status") ?: "pending"
                    val senderId = chatDoc.getString("senderId")
                    
                    // Only show dialog if:
                    // 1. We're the receiver
                    // 2. Status is pending
                    if (currentUserId != senderId && status == "pending") {
                        showAcceptanceDialog()
                    }
                }
            }
    }
} 