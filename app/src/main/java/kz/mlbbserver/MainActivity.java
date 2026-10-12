package kz.mlbbserver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    static final String MLBB_PACKAGE = "com.mobile.legends";

    static final String DEFAULTS =
            "Юго-Восточная Азия\tузел ~ Сингапур\tec2.ap-southeast-1.amazonaws.com\n"
                    + "Индонезия\tузел ~ Джакарта\tec2.ap-southeast-3.amazonaws.com\n"
                    + "Европа\tузел ~ Франкфурт\tec2.eu-central-1.amazonaws.com\n"
                    + "Россия / СНГ\tузел ~ Москва\tstorage.yandexcloud.net";

    static final int BG = Color.parseColor("#0D1122");
    static final int SURFACE = Color.parseColor("#151B34");
    static final int LINE = Color.parseColor("#262E52");
    static final int FG = Color.parseColor("#E8EBF7");
    static final int MUTED = Color.parseColor("#99A2C4");
    static final int ACCENT = Color.parseColor("#8D98FF");
    static final int GOOD = Color.parseColor("#4FD193");
    static final int WARN = Color.parseColor("#E6B455");
    static final int BAD = Color.parseColor("#FF7A70");

    static class Node {
        String name, hub, host;
        int ping = -2; // >=0 мс, -1 нет ответа, -2 ещё измеряется

        Node(String name, String hub, String host) {
            this.name = name;
            this.hub = hub;
            this.host = host;
        }
    }

    final List<Node> nodes = new ArrayList<>();
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService pool = Executors.newCachedThreadPool();

    SharedPreferences prefs;
    LinearLayout cards;
    Button launch;
    String selectedHost = null;
    int pending = 0;
    int gen = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("mlbb", MODE_PRIVATE);
        selectedHost = prefs.getString("sel", null);
        loadNodes();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(32), dp(16), dp(24));
        scroll.addView(root);

        root.addView(text("Выбор сервера MLBB", 26, FG, true));

        TextView sub = text("Пинг до узлов измеряется прямо сейчас, каждые 4 секунды.", 14, MUTED, false);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        root.addView(cards, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button add = new Button(this);
        add.setAllCaps(false);
        add.setTextSize(15);
        add.setTextColor(ACCENT);
        add.setBackground(rounded(BG, ACCENT, 1));
        add.setText("+ Добавить свой узел");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNodeDialog();
            }
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        root.addView(add, ap);

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
        lp.topMargin = dp(10);
        root.addView(launch, lp);

        TextView note = text(
                "Замер идёт от твоего телефона до узла (TCP, порт 443 или свой). "
                        + "Moonton не публикует адреса серверов игры, поэтому стандартные узлы "
                        + "это облачные точки регионов, а не сами серверы MLBB. "
                        + "Если знаешь адрес настоящего сервера, добавь его кнопкой выше. "
                        + "Долгое нажатие на карточку удаляет узел. "
                        + "В MLBB сервер привязан к аккаунту, приложение его не переключает. "
                        + "Реальный пинг смотри в матче, в левом верхнем углу.",
                12, MUTED, false);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        setContentView(scroll);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gen++;
        cycle(gen);
    }

    @Override
    protected void onPause() {
        super.onPause();
        gen++; // останавливает текущий цикл замеров
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }

    void loadNodes() {
        String raw = prefs.getString("nodes2", DEFAULTS);
        nodes.clear();
        for (String line : raw.split("\n")) {
            String[] f = line.split("\t");
            if (f.length == 3) nodes.add(new Node(f[0], f[1], f[2]));
        }
    }

    void saveNodes() {
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(n.name).append("\t").append(n.hub).append("\t").append(n.host);
        }
        prefs.edit().putString("nodes2", sb.toString()).apply();
    }

    void cycle(final int g) {
        if (g != gen) return;
        if (pending == 0 && !nodes.isEmpty()) {
            final List<Node> snapshot = new ArrayList<>(nodes);
            pending = snapshot.size();
            for (final Node n : snapshot) {
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        final int ms = measure(n.host);
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                n.ping = ms;
                                pending--;
                                render();
                            }
                        });
                    }
                });
            }
        }
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                cycle(g);
            }
        }, 4000);
    }

    // Лучшее из 4 TCP-подключений, мс; -1 если узел не ответил. Адрес: host или host:port.
    static int measure(String hostSpec) {
        try {
            String host = hostSpec;
            int port = 443;
            int c = hostSpec.lastIndexOf(':');
            if (c > 0 && hostSpec.indexOf(':') == c) {
                port = Integer.parseInt(hostSpec.substring(c + 1));
                host = hostSpec.substring(0, c);
            }
            InetAddress addr = InetAddress.getByName(host);
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                Socket s = new Socket();
                try {
                    long t0 = System.nanoTime();
                    s.connect(new InetSocketAddress(addr, port), 2500);
                    int ms = (int) Math.round((System.nanoTime() - t0) / 1e6);
                    if (ms < best) best = ms;
                } catch (Exception e) {
                    // неудачная попытка, пробуем дальше
                } finally {
                    try {
                        s.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            return best == Integer.MAX_VALUE ? -1 : best;
        } catch (Exception e) {
            return -1;
        }
    }

    static int sortKey(int p) {
        if (p >= 0) return p;
        if (p == -2) return 100000;
        return 200000;
    }

    void render() {
        List<Node> order = new ArrayList<>(nodes);
        Collections.sort(order, new Comparator<Node>() {
            @Override
            public int compare(Node a, Node b) {
                return sortKey(a.ping) - sortKey(b.ping);
            }
        });

        Node best = (!order.isEmpty() && order.get(0).ping >= 0) ? order.get(0) : null;
        Node eff = null;
        if (selectedHost != null) {
            for (Node n : nodes) {
                if (n.host.equals(selectedHost)) eff = n;
            }
        }
        if (eff == null) eff = best;

        cards.removeAllViews();
        for (final Node n : order) {
            int p = n.ping;
            boolean sel = n == eff;

            String label;
            int col;
            if (p >= 0) {
                label = p + " мс · " + (p < 60 ? "отлично" : p < 110 ? "играбельно"
                        : p < 160 ? "лаги в тимфайтах" : "тяжело");
                col = p < 110 ? GOOD : (p < 160 ? WARN : BAD);
            } else if (p == -2) {
                label = "измеряю…";
                col = MUTED;
            } else {
                label = "нет ответа";
                col = BAD;
            }

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(rounded(SURFACE, sel ? ACCENT : LINE, sel ? 2 : 1));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedHost = n.host;
                    prefs.edit().putString("sel", selectedHost).apply();
                    render();
                }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    confirmDelete(n);
                    return true;
                }
            });

            card.addView(text((n == best ? "★ " : "") + n.name, 18, FG, true));
            card.addView(text(n.hub, 12, MUTED, false));
            TextView pv = text(label, 20, col, true);
            pv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            pv.setPadding(0, dp(6), 0, 0);
            card.addView(pv);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            cards.addView(card, lp);
        }
        launch.setText(eff != null ? "Запустить MLBB · " + eff.name : "Запустить MLBB");
    }

    void confirmDelete(final Node n) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить узел?")
                .setMessage(n.name)
                .setPositiveButton("Удалить", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        nodes.remove(n);
                        if (n.host.equals(selectedHost)) {
                            selectedHost = null;
                            prefs.edit().remove("sel").apply();
                        }
                        saveNodes();
                        render();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    void addNodeDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        final EditText name = new EditText(this);
        name.setHint("Название, например Алматы");
        name.setSingleLine(true);
        box.addView(name);

        final EditText host = new EditText(this);
        host.setHint("Адрес или IP, например 1.2.3.4:443");
        host.setSingleLine(true);
        host.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(host);

        new AlertDialog.Builder(this)
                .setTitle("Добавить узел")
                .setView(box)
                .setPositiveButton("Добавить", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String nm = name.getText().toString().replaceAll("[\t\n]", " ").trim();
                        String h = host.getText().toString().trim()
                                .replaceFirst("^https?://", "");
                        int slash = h.indexOf('/');
                        if (slash >= 0) h = h.substring(0, slash);
                        if (nm.isEmpty() || h.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Нужны и название, и адрес", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        nodes.add(new Node(nm, "свой узел · " + h, h));
                        saveNodes();
                        render();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    void launchGame() {
        Intent i = getPackageManager().getLaunchIntentForPackage(MLBB_PACKAGE);
        if (i != null) {
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
