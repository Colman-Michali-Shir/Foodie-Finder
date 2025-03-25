package com.example.foodie_finder.data.model

import androidx.lifecycle.MutableLiveData
import com.example.foodie_finder.data.local.AppLocalDb
import com.example.foodie_finder.data.local.AppLocalDbRepository
import com.example.foodie_finder.data.local.Post
import com.example.foodie_finder.data.remote.CloudinaryModel
import com.example.foodie_finder.data.remote.FirebaseModel
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.Executors

class PostModel private constructor() {

    enum class LoadingState {
        LOADING,
        LOADED
    }

    private val database: AppLocalDbRepository = AppLocalDb.database
    private val firebaseModel = FirebaseModel.getInstance()
    private val cloudinaryModel = CloudinaryModel.getInstance()

    private var executor = Executors.newSingleThreadExecutor()

    val allPosts: MutableLiveData<List<Post>> = MutableLiveData<List<Post>>(emptyList())
    val savedPosts: MutableLiveData<List<String>> = MutableLiveData<List<String>>(emptyList())

    val loadingState: MutableLiveData<LoadingState> = MutableLiveData<LoadingState>()

    init {
        database.postDao().getAllPosts().observeForever { allPosts.postValue(it) }
        FirebaseAuth.getInstance().currentUser?.uid?.let {
            database.savedPostDao().getSavedPostsByUser(it)
                .observeForever { posts -> savedPosts.postValue(posts) }
        }


        //TODO: change it to save post
    }

    companion object {
        val shared = PostModel()
    }

    fun savePost(postId: String, callback: (Boolean) -> Unit) {
        firebaseModel.savePost(postId, callback)
    }

    fun removeSavedPost(postId: String, callback: (Boolean) -> Unit) {
        firebaseModel.removeSavedPost(postId, callback)
    }

    fun getSavedPosts() {
        firebaseModel.getSavedPosts { posts ->
            savedPosts.postValue(posts)
        }
    }

//    fun isPostSaved(postId: String, callback: (Boolean) -> Unit) {
//        firebaseModel.isPostSaved(postId, callback)
//    }

    fun refreshAllPosts() {
        loadingState.postValue(LoadingState.LOADING)
        val lastUpdated: Long = Post.lastUpdated
        firebaseModel.getAllPosts(lastUpdated) { posts ->
            executor.execute {
                var currentTime = lastUpdated

                for (post in posts) {
                    database.postDao().createPost(post)
                    post.lastUpdateTime?.let {
                        if (currentTime < it) {
                            currentTime = it
                        }
                    }
                }

                Post.lastUpdated = currentTime
                loadingState.postValue(LoadingState.LOADED)
            }
        }
    }
}