package com.example.foodie_finder.data.remote

import com.example.foodie_finder.base.Constants
import com.example.foodie_finder.base.EmptyCallback
import com.example.foodie_finder.base.GetAllPostsCallback
import com.example.foodie_finder.base.GetAllStudentsCallback
import com.example.foodie_finder.base.GetStudentByIdCallback
import com.example.foodie_finder.data.local.Post
import com.example.foodie_finder.data.local.Student
import com.example.foodie_finder.data.local.User
import com.example.foodie_finder.utils.extensions.toFirebaseTimestamp
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.memoryCacheSettings
import com.google.firebase.ktx.Firebase

class FirebaseModel private constructor() {

    private val database: FirebaseFirestore by lazy { Firebase.firestore }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    init {
        val setting = firestoreSettings {
            setLocalCacheSettings(memoryCacheSettings { })
        }

        database.firestoreSettings = setting
    }

    companion object {
        @Volatile
        private var instance: FirebaseModel? = null

        fun getInstance(): FirebaseModel {
            return instance ?: synchronized(this) {
                instance ?: FirebaseModel().also { instance = it }
            }
        }
    }

    fun getAllPosts(sinceLastUpdated: Long, callback: GetAllPostsCallback) {
        database.collection(Constants.COLLECTIONS.POSTS)
            .whereGreaterThanOrEqualTo(Post.LAST_UPDATE_TIME, sinceLastUpdated.toFirebaseTimestamp)
            .get()
            .addOnSuccessListener { postsJson ->
                val postsList: MutableList<Post> = mutableListOf()
                val tasks = mutableListOf<Task<DocumentSnapshot>>()

                for (postDoc in postsJson) {
                    val post = Post.fromJSON(postDoc.data)
                    val userRef = postDoc.getDocumentReference("postedBy")
                    if (userRef != null) {
                        val userTask = userRef.get().addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                val username =
                                    userDoc.getString("firstName") + userDoc.getString("lastName")
                                val profilePic = userDoc.getString("avatarUrl") ?: ""
                                post.username = username
                                post.userProfileImg = profilePic
                            }
                        }
                        tasks.add(userTask)
                    }
                    postsList.add(post)
                }

                Tasks.whenAllSuccess<DocumentSnapshot>(tasks).addOnSuccessListener {
                    callback(postsList) // Return the full list after all user data is fetched
                }.addOnFailureListener {
                    callback(emptyList()) // Handle failure case
                }


            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }

    fun getAllStudents(callback: GetAllStudentsCallback) {
        database.collection(Constants.COLLECTIONS.STUDENTS).get()
            .addOnSuccessListener { result ->
                val students = result.map { Student.fromJSON(it.data) }
                callback(students)
            }
            .addOnFailureListener {
                callback(emptyList()) // Return empty list on failure
            }
    }

    fun getStudentById(id: String, callback: GetStudentByIdCallback) {
        database.collection(Constants.COLLECTIONS.STUDENTS).document(id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    callback(Student.fromJSON(document.data ?: emptyMap()))
                } else {
                    throw NoSuchElementException("Student with ID $id not found")
                }
            }
            .addOnFailureListener {
                throw NoSuchElementException("Student with ID $id not found")
            }
    }

    fun add(student: Student, callback: EmptyCallback) {
        database.collection(Constants.COLLECTIONS.STUDENTS).document(student.id)
            .set(student.json)
            .addOnCompleteListener { callback() }
    }

    fun delete(student: Student, callback: EmptyCallback) {
        database.collection(Constants.COLLECTIONS.STUDENTS).document(student.id)
            .delete()
            .addOnCompleteListener { callback() }
    }

    fun update(student: Student, callback: EmptyCallback) {
        database.collection(Constants.COLLECTIONS.STUDENTS).document(student.id)
            .update(student.json)
            .addOnCompleteListener { callback() }
    }

    fun updateUser(user: User, callback: (Boolean) -> Unit) {
        database.collection(Constants.COLLECTIONS.USERS).document(user.id)
            .update(user.json)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getUser(callback: (User?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return callback(null)

        database.collection(Constants.COLLECTIONS.USERS).document(userId)
            .get()
            .addOnSuccessListener { document ->
                callback(
                    if (document.exists()) User.fromJSON(
                        document.data ?: emptyMap()
                    ) else null
                )
            }
            .addOnFailureListener { callback(null) }
    }

    fun signIn(
        email: String,
        password: String,
        callback: (Boolean, String?, List<String>?) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val user = auth.currentUser
                callback(true, "Login successful: ${user?.email}", null)
            }
            .addOnFailureListener {
                handleFirebaseError(callback, it.message)
            }
    }

    fun signOut() {
        auth.signOut()
    }

    fun signUp(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        callback: (Boolean, String?, List<String>?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val userId = auth.currentUser?.uid ?: return@addOnSuccessListener
                val user = User(
                    id = userId,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    avatarUrl = null,
                )
                database.collection(Constants.COLLECTIONS.USERS).document(userId)
                    .set(user.json)
                    .addOnSuccessListener {
                        callback(true, "User registered successfully", null)
                    }
                    .addOnFailureListener { e ->
                        callback(false, "Failed to save user data: ${e.message}", null)
                    }
            }
            .addOnFailureListener {
                handleFirebaseError(callback, it.message)
            }
    }

    private fun handleFirebaseError(
        callback: (Boolean, String?, List<String>?) -> Unit,
        errorMessage: String?
    ) {
        when {
            errorMessage?.contains(
                "email address is already in use",
                ignoreCase = true
            ) == true -> {
                callback(false, "Email is already registered", listOf("email"))
            }

            errorMessage?.contains(
                "The email address is badly formatted",
                ignoreCase = true
            ) == true -> {
                callback(false, "Invalid email format", listOf("email"))
            }

            errorMessage?.contains(
                "Password should be at least 6 characters",
                ignoreCase = true
            ) == true -> {
                callback(false, "Password must be at least 6 characters", listOf("password"))
            }

            errorMessage?.contains(
                "The supplied auth credential is incorrect",
                ignoreCase = true
            ) == true -> {
                callback(false, "Email or password is incorrect", listOf("email", "password"))
            }

            else -> {
                callback(false, errorMessage ?: "Unknown error", null)
            }
        }
    }

    fun getSavedPosts(callback: (List<Post>) -> Unit) {
        val userId = auth.currentUser?.uid ?: return callback(emptyList())

        database.collection(Constants.COLLECTIONS.SAVED_POSTS)
            .whereEqualTo("userId", userId) // Filter saved posts by user ID
            .get()
            .addOnSuccessListener { result ->
                // TODO: do it as reference
                val postIds =
                    result.documents.mapNotNull { it.getString("postId") } // Extract post IDs

                if (postIds.isEmpty()) {
                    callback(emptyList()) // No saved posts
                    return@addOnSuccessListener
                }

                // Fetch actual posts by IDs
                database.collection(Constants.COLLECTIONS.POSTS)
                    .whereIn("id", postIds) // Fetch posts with matching IDs
                    .get()
                    .addOnSuccessListener { postResult ->
                        val posts =
                            postResult.documents.mapNotNull { it.toObject(Post::class.java) }
                        callback(posts) // Return the list of saved posts
                    }
                    .addOnFailureListener {
                        callback(emptyList()) // Handle failure case
                    }
            }
            .addOnFailureListener {
                callback(emptyList()) // Handle failure case
            }
    }


    fun savePost(postId: String, callback: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return callback(false)

        val savedPostRef = database.collection(Constants.COLLECTIONS.SAVED_POSTS)
            .document("$userId-$postId")

        val savedPost = mapOf(
            "userId" to userId,
            "postId" to postId,
            "savedAt" to System.currentTimeMillis().toFirebaseTimestamp
        )

        savedPostRef.set(savedPost)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun removeSavedPost(postId: String, callback: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return callback(false)

        database.collection(Constants.COLLECTIONS.SAVED_POSTS)
            .document("$userId-$postId")
            .delete()
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }
}
