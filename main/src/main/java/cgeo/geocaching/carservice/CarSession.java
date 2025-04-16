package cgeo.geocaching.carservice;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.Screen;
import androidx.car.app.Session;

public class CarSession extends Session {
    @NonNull
    @Override
    public Screen onCreateScreen(@NonNull final Intent intent) {
        return new MainCarScreen(getCarContext());
    }
}
