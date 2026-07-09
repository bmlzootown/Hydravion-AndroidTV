package ml.bmlzootown.hydravion.browse

import androidx.leanback.widget.HeaderItem

/**
 * Collapsible subscription header shown in the browse sidebar.
 * Press OK on the header to expand/collapse its channel list.
 */
class SubscriptionHeaderItem(
    id: Long,
    name: String,
    val creatorGUID: String,
    val iconUrl: String? = null
) : HeaderItem(id, name) {

    var expanded: Boolean = false
}
