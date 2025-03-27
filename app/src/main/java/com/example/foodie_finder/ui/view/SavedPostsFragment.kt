package com.example.foodie_finder.ui.view

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.foodie_finder.adapter.Post.PostsAdapter
import com.example.foodie_finder.data.local.Post
import com.example.foodie_finder.data.model.PostModel
import com.example.foodie_finder.databinding.FragmentSavedPostsBinding
import com.example.foodie_finder.interfaces.OnItemClickListener
import com.example.foodie_finder.ui.viewModel.SavedPostsViewModel

class SavedPostsFragment : Fragment() {
    private var adapter: PostsAdapter? = null
    private var binding: FragmentSavedPostsBinding? = null

    private val viewModel: SavedPostsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSavedPostsBinding.inflate(inflater, container, false)

        binding?.savedPostsList?.setHasFixedSize(true)
        binding?.savedPostsList?.layoutManager = LinearLayoutManager(context)

        adapter = PostsAdapter(
            viewModel.posts.value ?: emptyList(),
            viewModel.savedPosts.value ?: emptyList(),
            onSavePost = viewModel::savePost,
            onRemoveSavePost = viewModel::removeSavedPost,
            isSavedPostsMode = true,
        )

        viewModel.posts.observe(viewLifecycleOwner) { posts ->

            Log.d("TAG", "Observed posts: $posts") // Debug log

            adapter?.updateAllPosts(posts)
            adapter?.notifyDataSetChanged()
        }

        viewModel.savedPosts.observe(viewLifecycleOwner) { posts ->

            Log.d("TAG", "Observed posts: $posts") // Debug log

            adapter?.updateSavedPosts(posts)
            adapter?.notifyDataSetChanged()
        }


        binding?.swipeToRefresh?.setOnRefreshListener {
            viewModel.refreshAllPosts()
            viewModel.refreshSavedPosts()
        }

        PostModel.shared.loadingState.observe(viewLifecycleOwner) { state ->
            binding?.swipeToRefresh?.isRefreshing = (state == PostModel.LoadingState.LOADING)
        }

        adapter?.listener = object : OnItemClickListener {
            override fun onItemClick(post: Post?) {
                Log.d("TAG", "On click post $post")
                post?.let { clickedPost ->
//                    val action =
//                        HomeFragmentDirections.actionStudentsListFragmentToStudentDetailsFragment(
//                            clickedPost.id
//                        )
//                    binding?.root?.let { Navigation.findNavController(it).navigate(action) }
                }

            }
        }

        binding?.savedPostsList?.adapter = adapter

        return binding?.root
    }

    override fun onResume() {
        super.onResume()
        getAllPosts()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    private fun getAllPosts() {
        viewModel.refreshAllPosts()
        viewModel.refreshSavedPosts()
    }
}