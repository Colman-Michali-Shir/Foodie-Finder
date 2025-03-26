package com.example.foodie_finder.data.model

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import com.example.foodie_finder.base.Callback
import com.example.foodie_finder.data.local.AppLocalDb
import com.example.foodie_finder.data.local.AppLocalDbRepository
import com.example.foodie_finder.data.local.FirebasePost
import com.example.foodie_finder.data.local.Post
import com.example.foodie_finder.data.remote.CloudinaryModel
import com.example.foodie_finder.data.remote.FirebaseModel
import com.google.firebase.Timestamp
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

    val allPosts: MutableLiveData<List<Post>> = MutableLiveData<List<Post>>()
    val loadingState: MutableLiveData<LoadingState> = MutableLiveData<LoadingState>()

    init {
        database.postDao().getAllPosts().observeForever { allPosts.postValue(it) }
    }

    companion object {
        val shared = PostModel()
    }


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

    fun createPost(post: FirebasePost, image: Bitmap?, callback: Callback<Pair<Boolean, String?>>) {

        firebaseModel.createPost(post) { isSuccessful ->
            if (isSuccessful) {
                image?.let {
                    cloudinaryModel.uploadImageToCloudinary(
                        image = it,
                        name = post.id,
                        onSuccess = { url ->
                            val newPost = post.copy(imgUrl = url)
                            firebaseModel.createPost(newPost) { isSuccessful ->
                                callback(
                                    Pair(
                                        isSuccessful,
                                        if (isSuccessful) null else "Can't create post, please try again"
                                    )
                                )
                            }
                        },
                        onError = { callback(Pair(true, null)) }
                    )
                } ?: callback(Pair(true, null))

            } else {
                callback(Pair(false, "Can't create post, please try again"))
            }
        }
    }

    fun updatePost(post: Post, image: Bitmap?, callback: Callback<Pair<Boolean, String?>>) {
        val userRef = UserModel.shared.getConnectedUserRef() ?: return
        val editedPost = FirebasePost(
            id = post.id,
            postedBy = userRef,
            title = post.title,
            content = post.content,
            rating = post.rating,
            imgUrl = post.imgUrl,
            lastUpdateTime = Timestamp.now().toDate().time,
            creationTime = post.creationTime
        )

        createPost(editedPost, image) { (isSuccessful, errorMessage) ->
            callback(
                Pair(
                    isSuccessful,
                    if (errorMessage == null) null else "Can't update post, please try again"
                )
            )
        }
    }

    fun deletePost(postId: String, callback: Callback<Pair<Boolean, String?>>) {
        firebaseModel.deletePost(postId) { isSuccessful ->
            if (isSuccessful) {
                executor.execute {
                    database.postDao().deletePost(postId)
                }
            }
            callback(
                Pair(
                    isSuccessful,
                    if (isSuccessful) null else "Can't delete post, please try again"
                )
            )
        }
    }

}