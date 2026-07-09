package ml.bmlzootown.hydravion.browse

import androidx.leanback.widget.HeaderItem

/**
 * Header item that can carry an icon URL (e.g. a channel icon) for the sidebar.
 */
class IconHeaderItem(
    id: Long,
    name: String,
    val iconUrl: String? = null,
    val creatorGUID: String? = null,
    val indented: Boolean = false
) : HeaderItem(id, name)
