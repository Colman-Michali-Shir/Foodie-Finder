package com.example.foodie_finder.interfaces

import com.example.foodie_finder.data.local.Post

interface OnItemClickListener {
    fun onItemClick(post: Post?)
}
