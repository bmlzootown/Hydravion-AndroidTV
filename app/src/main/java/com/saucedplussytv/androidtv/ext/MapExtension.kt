package com.saucedplussytv.androidtv.ext

import com.saucedplussytv.androidtv.models.Video

fun Map<String, ArrayList<Video>>.getBlogPostIdsFromCreatorMap() =
    this.flatMap { it.value }.map { video: Video -> video.id }