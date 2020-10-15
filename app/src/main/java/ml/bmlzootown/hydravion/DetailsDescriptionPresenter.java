package ml.bmlzootown.hydravion;

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;

import ml.bmlzootown.hydravion.models.Video;

public class DetailsDescriptionPresenter extends AbstractDetailsDescriptionPresenter {

    @Override
    protected void onBindDescription(ViewHolder viewHolder, Object item) {
        Video vid = (Video) item;

        if (vid != null) {
            viewHolder.getTitle().setText(vid.getTitle());
            viewHolder.getSubtitle().setText(vid.getReleaseDate());
            viewHolder.getBody().setText(vid.getDescription());
        }
    }
}
