package cgeo.geocaching.carservice;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;

import android.graphics.drawable.Icon;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.GridItem;
import androidx.car.app.model.GridTemplate;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.OnClickListener;
import androidx.car.app.model.Template;
import androidx.core.graphics.drawable.IconCompat;

@RequiresApi(23)
public class MainCarScreen extends Screen {

    public MainCarScreen(final CarContext carContext) {
        super(carContext);
        invalidate();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        final ItemList itemList = new ItemList.Builder()
                .addItem(getGridItem(R.drawable.ic_menu_mapmode, "Follow me", () -> getScreenManager().push(new FollowMeScreen(getCarContext()))))
                .addItem(getGridItem(R.drawable.ic_menu_list, "Cache list", () -> getScreenManager().push(new SearchScreen(getCarContext(), SearchScreen.SEARCHCONTEXT.SEARCH_LISTS))))
                .addItem(getGridItem(R.drawable.ic_menu_route, "Track / Route", () -> getScreenManager().push(new SearchScreen(getCarContext(), SearchScreen.SEARCHCONTEXT.SEARCH_TRACKS))))
                .build();
        return new GridTemplate.Builder()
            .setTitle("Welcome to c:geo!")
            .setLoading(false)
            .setSingleList(itemList)
            .build();
    }

    private GridItem getGridItem(@DrawableRes final int iconResId, final String label, final OnClickListener onClickListener) {
        final int colorAccent = CgeoApplication.getInstance().getColor(R.color.colorAccent);
        return new GridItem.Builder()
                .setTitle(label)
                .setOnClickListener(onClickListener)
                .setImage(new CarIcon.Builder(IconCompat.createFromIcon(getCarContext(), Icon.createWithResource(getCarContext(), iconResId))).setTint(CarColor.createCustom(colorAccent, colorAccent)).build())
                .build();
    }
}
