package ml.bmlzootown.hydravion;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

public class PopupCreator {

    private PopupWindow popupWindow;
    private String token;

    public PopupCreator(String token) {
        this.token = token;
    }

    public void showPopupWindow(final View view) {
        //Create a View object yourself through inflater
        @SuppressLint("InflateParams") View popupView = LayoutInflater.from(view.getContext()).inflate(R.layout.popup_layout, null);

        //Specify the length and width through constants
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = 300;

        //Make Inactive Items Outside Of PopupWindow
        boolean focusable = true;

        //Create a window with our parameters
        popupWindow = new PopupWindow(popupView, width, height, focusable);

        //Set the location of the window on the screen
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);

        //Initialize the elements of our window, install the handler

        TextView test2 = popupView.findViewById(R.id.titleText);
        test2.setText(R.string.codeTitle);

        ((TextView) popupView.findViewById(R.id.messageText)).setText(view.getContext().getString(R.string.format_code_instructions, token));
    }

    public void closePopup() {
        popupWindow.dismiss();
    }

}
