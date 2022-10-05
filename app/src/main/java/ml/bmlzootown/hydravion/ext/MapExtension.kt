package ml.bmlzootown.hydravion.ext

import ml.bmlzootown.hydravion.models.Video

fun Map<String, ArrayList<Video>>.getBlogPostIdsFromCreatorMap() =
    this.flatMap { it.value }.map { video: Video -> video.id }