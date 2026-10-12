package kz.mlbbserver;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    static final String MLBB_PACKAGE = "com.mobile.legends";

    static final String[] CITY_NAMES = {
        "Семей", "Усть-Каменогорск", "Павлодар", "Караганда", "Астана", "Алматы",
        "Шымкент", "Актобе", "Атырау", "Москва", "Санкт-Петербург", "Краснодар",
        "Екатеринбург", "Новосибирск", "Владивосток", "Минск", "Ташкент", "Бишкек",
        "Душанбе", "Ереван", "Баку", "Кишинёв"
    };
    static final double[][] CITY_XY = {
        {50.41, 80.23}, {49.95, 82.61}, {52.29, 76.97}, {49.80, 73.09}, {51.17, 71.43}, {43.24, 76.89},
        {42.32, 69.59}, {50.28, 57.21}, {47.11, 51.92}, {55.76, 37.62}, {59.93, 30.34}, {45.04, 38.98},
        {56.84, 60.60}, {55.03, 82.92}, {43.12, 131.89}, {53.90, 27.57}, {41.30, 69.24}, {42.87, 74.59},
        {38.56, 68.77}, {40.18, 44.51}, {40.41, 49.87}, {47.01, 28.86}
    };

    static final String[] SRV_NAMES = {"Юго-Восточная Азия", "Европа", "Россия / СНГ"};
    static final String[] SRV_HUBS = {"узел ~ Сингапур", "узел ~ Франкфурт", "узел ~ Москва"};
    static final double[][] SRV_XY = {{1.35, 103.82}, {50.11, 8.68}, {55.76, 37.62}};

    static final int BG = Color.parseColor("#0D1122");
    static final int SURFACE = Color.parseColor("#151B34");
    static final int LINE = Color.parseColor("#262E52");
    static final int FG = Color.parseColor("#E8EBF7");
    static final int MUTED = Color.parseColor("#99A2C4");
    static final int ACCENT = Color.parseColor("#8D98FF");
    static final int GOOD = Color.parseColor("#4FD193");
    static final int WARN = Color.parseColor("#E6B455");
    static final int BAD = Color.parseColor("#FF7A70");

    SharedPreferences prefs;
    LinearLayout cards;
    Button launch;
    int city = 0;
    int selected = -1;

    static class Row {
        int idx;
        int dist;
        int ping;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("mlbb", MODE_PRIVATE);
        city = Math.min(prefs.getInt("city", 0), CITY_NAMES.length - 1);
        selected = prefs.getInt("server", -1);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(32), dp(16), dp(24));
        scroll.addView(root);

        TextView title = text("Выбор сервера MLBB", 26, FG, true);
        root.addView(title);

        TextView sub = text("Выбери город, выбери сервер, запусти игру.", 14, MUTED, false);
        sub.setPadding(0, dp(4), 0, dp(20));
        root.addView(sub);

        TextView cityLbl = text("ТВОЙ ГОРОД", 12, MUTED, false);
        cityLbl.setLetterSpacing(0.08f);
        root.addView(cityLbl);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CITY_NAMES);
        spinner.setAdapter(ad);
        spinner.setSelection(city);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos != city) {
                    city = pos;
                    prefs.edit().putInt("city", city).apply();
                }
                render();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });
        root.addView(spinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(12);
        root.addView(cards, cp);

        launch = new Button(this);
        launch.setAllCaps(false);
        launch.setTextSize(16);
        launch.setTypeface(Typeface.DEFAULT_BOLD);
        launch.setTextColor(BG);
        launch.setBackground(rounded(ACCENT, ACCENT, 0));
        launch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchGame();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(8);
        root.addView(launch, lp);

        TextView note = text(
                "Пинг здесь оценка по расстоянию, а не замер. В самой MLBB сервер привязан к аккаунту, "
                        + "приложение его не переключает: оно показывает лучший регион и запускает игру. "
                        + "Реальный пинг смотри в матче, в левом верхнем углу.",
                12, MUTED, false);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        setContentView(scroll);
    }

    void render() {
        double[] c = CITY_XY[city];
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < SRV_NAMES.length; i++) {
            Row r = new Row();
            r.idx = i;
            r.dist = (int) Math.round(km(c[0], c[1], SRV_XY[i][0], SRV_XY[i][1]));
            r.ping = (int) Math.round(2.0 * r.dist * 1.5 / 200.0 + 12);
            rows.add(r);
        }
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                return a.ping - b.ping;
            }
        });

        if (selected < 0) selected = rows.get(0).idx;

        cards.removeAllViews();
        for (int k = 0; k < rows.size(); k++) {
            final Row r = rows.get(k);
            boolean sel = r.idx == selected;
            int col = r.ping < 110 ? GOOD : (r.ping < 160 ? WARN : BAD);
            String grade = r.ping < 60 ? "отлично" : r.ping < 110 ? "играбельно"
                    : r.ping < 160 ? "лаги в тимфайтах" : "тяжело";

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(rounded(SURFACE, sel ? ACCENT : LINE, sel ? 2 : 1));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selected = r.idx;
                    prefs.edit().putInt("server", selected).apply();
                    render();
                }
            });

            card.addView(text((k == 0 ? "★ " : "") + SRV_NAMES[r.idx], 18, FG, true));
            card.addView(text(SRV_HUBS[r.idx], 12, MUTED, false));
            TextView ping = text("~" + r.ping + " мс · " + grade, 20, col, true);
            ping.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            ping.setPadding(0, dp(6), 0, 0);
            card.addView(ping);
            card.addView(text(r.dist + " км от города", 12, MUTED, false));

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.bottomMargin = dp(10);
            cards.addView(card, p);
        }
        launch.setText("Запустить MLBB · " + SRV_NAMES[selected]);
    }

    void launchGame() {
        Intent i = getPackageManager().getLaunchIntentForPackage(MLBB_PACKAGE);
        if (i != null) {
            Toast.makeText(this, "Запускаю MLBB. Регион выбранного сервера: "
                    + SRV_NAMES[selected], Toast.LENGTH_SHORT).show();
            startActivity(i);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + MLBB_PACKAGE)));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + MLBB_PACKAGE)));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "MLBB не найдена на этом устройстве", Toast.LENGTH_LONG).show();
            }
        }
    }

    static double km(double lat1, double lon1, double lat2, double lon2) {
        double r = Math.PI / 180;
        double dLat = (lat2 - lat1) * r, dLon = (lon2 - lon1) * r;
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1 * r) * Math.cos(lat2 * r) * Math.pow(Math.sin(dLon / 2), 2);
        return 12742 * Math.asin(Math.sqrt(h));
    }

    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.START);
        return t;
    }

    GradientDrawable rounded(int fill, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(10));
        d.setStroke(dp(Math.max(strokeDp, 0)), stroke);
        return d;
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
                                                          }
