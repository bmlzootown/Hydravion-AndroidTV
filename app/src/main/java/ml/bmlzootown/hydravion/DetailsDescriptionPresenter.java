package ml.bmlzootown.hydravion;

import android.annotation.SuppressLint;

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;

import org.ocpsoft.prettytime.PrettyTime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import ml.bmlzootown.hydravion.models.Video;

public class DetailsDescriptionPresenter extends AbstractDetailsDescriptionPresenter {

    @Override
    protected void onBindDescription(ViewHolder viewHolder, Object item) {
        Video vid = (Video) item;

        if (vid != null) {
            @SuppressLint("SimpleDateFormat") SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = null;
            try {
                date = input.parse(vid.getReleaseDate());
            } catch (ParseException e) {
                e.printStackTrace();
            }
            PrettyTime p = new PrettyTime();
            String elapsed = p.format(date);

            viewHolder.getTitle().setText(vid.getTitle());
            //viewHolder.getSubtitle().setText(vid.getReleaseDate());
            viewHolder.getSubtitle().setText(elapsed);
            viewHolder.getBody().setText(vid.getDescription());
        }
    }
}
