package kz.mlbbserver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    static final String MLBB_PACKAGE = "com.mobile.legends";

    // имя \t подпись \t адрес. Формат адреса: host, host:port или ping:host (ICMP)
    static final String DEFAULTS =
            "Казахстан · Алматы\tбоевой сервер (Казахтелеком)\t95.56.249.147:5551\n"
          + "Казахстан · Алматы (облако)\tбоевой сервер (Alibaba Cloud)\t43.109.87.162:5553\n"
          + "Россия · Новосибирск\tузел проверки пинга\tping:146.185.199.88\n"
          + "Россия · Москва\tузел проверки пинга\tping:154.85.91.164\n"
          + "Россия · Хабаровск\tузел проверки пинга\tping:92.223.80.21\n"
          + "Турция · Стамбул\tузел проверки пинга\tping:185.23.183.5\n"
          + "Польша · Варшава\tузел проверки пинга\tping:5.188.108.230\n"
          + "Германия · Франкфурт\tузел проверки пинга\tping:148.153.35.94\n"
          + "Нидерланды · Амстердам\tузел проверки пинга\tping:104.166.188.242\n"
          + "Испания · Мадрид\tузел проверки пинга\tping:98.98.171.134\n"
          + "Сингапур · лобби MLBB\tсессия игры\t161.202.222.219:30102\n";

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
        int ping = -2; // -2 замеряется, -1 нет ответа
    }

    final List<Node> nodes = new ArrayList<>();
    final Handler handler = new Handler(Looper.getMainLooper());
    final ExecutorService pool = Executors.newCachedThreadPool();
    int gen = 0;
    boolean running = false;

    SharedPreferences prefs;
    LinearLayout cards;
    TextView bestLine;
    String selHost = "";
    TextView diagResult;
    Button diagBtn;
    boolean diagRunning = false;
    TextView histView;
    volatile String lastResult = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("mlbb", MODE_PRIVATE);
        selHost = prefs.getString("sel", "");
        loadNodes();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(32), dp(16), dp(24));
        scroll.addView(root);

        root.addView(text("Пинг до серверов MLBB", 26, FG, true));
        TextView sub = text("Живой замер каждые 4 секунды. Лучший узел помечен ★.", 14, MUTED, false);
        sub.setPadding(0, dp(4), 0, dp(12));
        root.addView(sub);

        bestLine = text("Замеряю…", 16, GOOD, true);
        bestLine.setPadding(0, 0, 0, dp(8));
        root.addView(bestLine);

        LinearLayout diag = new LinearLayout(this);
        diag.setOrientation(LinearLayout.VERTICAL);
        diag.setPadding(dp(16), dp(12), dp(16), dp(12));
        diag.setBackground(rounded(SURFACE, LINE, 1));
        diag.addView(text("Диагностика сети", 17, FG, true));
        diagResult = text("20 проб: потери, джиттер, пинг. Тестируется выбранный узел (или лучший). "
                + "Прогони тест на Wi-Fi и на мобильной сети, и приложение сравнит их.", 13, MUTED, false);
        diagResult.setPadding(0, dp(4), 0, dp(8));
        diag.addView(diagResult);
        diagBtn = new Button(this);
        diagBtn.setAllCaps(false);
        diagBtn.setText("Запустить тест (~10 сек)");
        diagBtn.setTextColor(ACCENT);
        diagBtn.setBackground(rounded(BG, ACCENT, 1));
        diagBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDiag();
            }
        });
        diag.addView(diagBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        LinearLayout.LayoutParams dgp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dgp.bottomMargin = dp(12);
        root.addView(diag, dgp);

        Button share = smallBtn("Поделиться результатом", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareResult();
            }
        });
        LinearLayout.LayoutParams shp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        shp.topMargin = dp(6);
        diag.addView(share, shp);

        // история тестов
        LinearLayout hist = new LinearLayout(this);
        hist.setOrientation(LinearLayout.VERTICAL);
        hist.setPadding(dp(16), dp(12), dp(16), dp(12));
        hist.setBackground(rounded(SURFACE, LINE, 1));
        hist.addView(text("История тестов", 17, FG, true));
        histView = text("", 12, MUTED, false);
        histView.setPadding(0, dp(4), 0, dp(8));
        hist.addView(histView);
        hist.addView(smallBtn("Очистить", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().remove("hist").remove("d_wifi").remove("d_mob").apply();
                renderHist();
            }
        }), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.bottomMargin = dp(12);
        root.addView(hist, hp);
        renderHist();

        // быстрые настройки сети
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setPadding(dp(16), dp(12), dp(16), dp(12));
        tools.setBackground(rounded(SURFACE, LINE, 1));
        tools.addView(text("Быстрая настройка сети", 17, FG, true));
        TextView td = text("Открывают системные настройки Android. Игру и её файлы приложение не трогает.",
                12, MUTED, false);
        td.setPadding(0, dp(4), 0, dp(8));
        tools.addView(td);
        String[][] items = {
                {"Настройки Wi-Fi (лучше 5 ГГц, ближе к роутеру)", Settings.ACTION_WIFI_SETTINGS},
                {"Мобильная сеть (выбери LTE/5G)", Settings.ACTION_WIRELESS_SETTINGS},
                {"Экономия трафика (выключи на время игры)", "android.settings.DATA_USAGE_SETTINGS"},
                {"Экономия заряда (выключи на время игры)", Settings.ACTION_BATTERY_SAVER_SETTINGS}
        };
        for (final String[] it : items) {
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
            tp.bottomMargin = dp(6);
            tools.addView(smallBtn(it[0], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSettings(it[1]);
                }
            }), tp);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = dp(12);
        root.addView(tools, tlp);

        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        root.addView(cards, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button add = new Button(this);
        add.setAllCaps(false);
        add.setText("+ Добавить свой узел");
        add.setTextColor(ACCENT);
        add.setBackground(rounded(SURFACE, LINE, 1));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addDialog();
            }
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        ap.topMargin = dp(4);
        root.addView(add, ap);

        Button launch = new Button(this);
        launch.setAllCaps(false);
        launch.setText("Запустить MLBB");
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
                "Приложение только показывает пинг и запускает игру. Сервер аккаунта в MLBB "
              + "переключить нельзя: он привязан к аккаунту, а бой подбирает сама игра. "
              + "Нажми на карточку, чтобы отметить узел, долгое нажатие удаляет его.",
                12, MUTED, false);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        setContentView(scroll);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        final int g = ++gen;
        cycle(g);
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        gen++;
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }

    void cycle(final int g) {
        if (!running || g != gen) return;
        // во время диагностики живой замер пропускаем, чтобы не искажать результат
        List<Node> snap = diagRunning ? new ArrayList<Node>() : new ArrayList<Node>(nodes);
        for (final Node n : snap) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    final int p = measure(n.host);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (g != gen) return;
                            n.ping = p;
                            render();
                        }
                    });
                }
            });
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                cycle(g);
            }
        }, 4000);
    }

    // ---------- замер ----------

    static int measure(String spec) {
        try {
            if (spec.startsWith("ping:")) return pingIcmp(spec.substring(5));
            return tcpConnect(spec);
        } catch (Throwable t) {
            return -1;
        }
    }

    static int pingIcmp(String host) {
        Process pr = null;
        try {
            pr = new ProcessBuilder("ping", "-c", "3", "-W", "2", host)
                    .redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            Pattern pat = Pattern.compile("time[=<]([0-9.]+)\\s*ms");
            double best = -1;
            String line;
            while ((line = r.readLine()) != null) {
                Matcher m = pat.matcher(line);
                if (m.find()) {
                    double v = Double.parseDouble(m.group(1));
                    if (best < 0 || v < best) best = v;
                }
            }
            return best < 0 ? -1 : (int) Math.round(best);
        } catch (Exception e) {
            return -1;
        } finally {
            if (pr != null) pr.destroy();
        }
    }

    static int tcpConnect(String spec) {
        String host = spec;
        int port = 443;
        int i = spec.lastIndexOf(':');
        if (i > 0) {
            try {
                port = Integer.parseInt(spec.substring(i + 1));
                host = spec.substring(0, i);
            } catch (NumberFormatException ignored) {
            }
        }
        int best = -1;
        for (int k = 0; k < 4; k++) {
            Socket s = new Socket();
            try {
                long t0 = System.nanoTime();
                s.connect(new InetSocketAddress(host, port), 2500);
                int ms = (int) Math.round((System.nanoTime() - t0) / 1e6);
                if (best < 0 || ms < best) best = ms;
            } catch (Exception ignored) {
            } finally {
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
        }
        return best;
    }

    // ---------- диагностика ----------

    static final int DIAG_N = 20;

    Node target() {
        for (Node n : nodes) if (n.host.equals(selHost)) return n;
        Node best = null;
        for (Node n : nodes) if (n.ping >= 0 && (best == null || n.ping < best.ping)) best = n;
        return best != null ? best : (nodes.isEmpty() ? null : nodes.get(0));
    }

    boolean isWifi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Throwable t) {
            return false;
        }
    }

    // одна проба, мс; -1 = потеря
    static double sampleOnce(String spec) {
        try {
            if (spec.startsWith("ping:")) return pingOne(spec.substring(5));
            return tcpOne(spec);
        } catch (Throwable t) {
            return -1;
        }
    }

    static double pingOne(String host) {
        Process pr = null;
        try {
            pr = new ProcessBuilder("ping", "-c", "1", "-W", "1", host)
                    .redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            Pattern pat = Pattern.compile("time[=<]([0-9.]+)\\s*ms");
            String line;
            while ((line = r.readLine()) != null) {
                Matcher m = pat.matcher(line);
                if (m.find()) return Double.parseDouble(m.group(1));
            }
            return -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (pr != null) pr.destroy();
        }
    }

    static double tcpOne(String spec) {
        String host = spec;
        int port = 443;
        int i = spec.lastIndexOf(':');
        if (i > 0) {
            try {
                port = Integer.parseInt(spec.substring(i + 1));
                host = spec.substring(0, i);
            } catch (NumberFormatException ignored) {
            }
        }
        Socket s = new Socket();
        try {
            long t0 = System.nanoTime();
            s.connect(new InetSocketAddress(host, port), 1500);
            return (System.nanoTime() - t0) / 1e6;
        } catch (Exception e) {
            return -1;
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    void startDiag() {
        if (diagRunning) return;
        final Node t = target();
        if (t == null) return;
        diagRunning = true;
        diagBtn.setEnabled(false);
        diagResult.setText("Тест: " + t.name + "…");
        final boolean wifi = isWifi();
        pool.execute(new Runnable() {
            @Override
            public void run() {
                final double[] r = new double[DIAG_N];
                for (int i = 0; i < DIAG_N; i++) r[i] = -1;
                for (int i = 0; i < DIAG_N; i++) {
                    r[i] = sampleOnce(t.host);
                    final int done = i + 1;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            diagResult.setText("Тест: " + t.name + " · " + done + "/" + DIAG_N);
                        }
                    });
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                final String res = analyze(t, r, wifi);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        diagResult.setText(res);
                        diagBtn.setEnabled(true);
                        diagRunning = false;
                        renderHist();
                    }
                });
            }
        });
    }

    String analyze(Node t, double[] r, boolean wifi) {
        int ok = 0, jn = 0;
        double min = 1e9, max = 0, sum = 0, jit = 0;
        for (int i = 0; i < r.length; i++) {
            if (r[i] < 0) continue;
            ok++;
            sum += r[i];
            min = Math.min(min, r[i]);
            max = Math.max(max, r[i]);
            if (i > 0 && r[i - 1] >= 0) {
                jit += Math.abs(r[i] - r[i - 1]);
                jn++;
            }
        }
        if (ok == 0) return "Нет ответа от узла «" + t.name + "». Выбери другой узел и повтори.";
        double avg = sum / ok;
        double jitter = jn > 0 ? jit / jn : 0;
        double loss = (r.length - ok) * 100.0 / r.length;

        String verdict, hint;
        if (loss >= 5 || jitter >= 30 || avg >= 150) verdict = "Плохо";
        else if (loss >= 1 || jitter >= 15 || avg >= 100) verdict = "Средне";
        else verdict = "Хорошо";

        if (loss >= 1 || jitter >= 15) {
            hint = "Скачки и потери идут из твоего канала (слабый сигнал, перегрузка Wi-Fi или сети оператора), "
                 + "а не из-за сервера. Подойди ближе к роутеру, закрой загрузки, сравни с другой сетью.";
        } else if (avg >= 100) {
            hint = "Канал стабильный, но задержка высокая: узел далеко или у провайдера длинный маршрут.";
        } else {
            hint = "Канал стабильный и быстрый. Лаги в игре с такой сетью не из-за интернета.";
        }

        String line = Math.round(avg) + " мс, джиттер " + Math.round(jitter)
                + " мс, потери " + Math.round(loss) + "%";
        prefs.edit().putString(wifi ? "d_wifi" : "d_mob", line + " (" + t.name + ")").apply();
        saveHist(wifi ? "Wi-Fi" : "моб.", t.name, Math.round(avg), Math.round(jitter), Math.round(loss));

        StringBuilder sb = new StringBuilder();
        sb.append(verdict).append(" · ").append(wifi ? "Wi-Fi" : "мобильная сеть").append("\n");
        sb.append("Узел: ").append(t.name).append("\n");
        sb.append("Пинг: мин ").append(Math.round(min)).append(" / сред ").append(Math.round(avg))
                .append(" / макс ").append(Math.round(max)).append(" мс\n");
        sb.append("Джиттер: ").append(Math.round(jitter)).append(" мс\n");
        sb.append("Потери: ").append(Math.round(loss)).append("% (").append(r.length - ok)
                .append(" из ").append(r.length).append(")\n\n");
        sb.append(hint);
        String w = prefs.getString("d_wifi", null), m = prefs.getString("d_mob", null);
        if (w != null || m != null) {
            sb.append("\n\nПоследние тесты:");
            sb.append("\nWi-Fi: ").append(w != null ? w : "не тестировали");
            sb.append("\nМобильная: ").append(m != null ? m : "не тестировали");
        }
        lastResult = sb.toString();
        return lastResult;
    }

    void saveHist(String net, String node, long avg, long jit, long loss) {
        String old = prefs.getString("hist", "");
        String row = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(new Date())
                + "\t" + net + "\t" + node + "\t" + avg + "\t" + jit + "\t" + loss;
        String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, lines.length - 14); i < lines.length; i++) sb.append(lines[i]).append('\n');
        sb.append(row);
        prefs.edit().putString("hist", sb.toString()).apply();
    }

    void renderHist() {
        String s = prefs.getString("hist", "");
        if (s.isEmpty()) {
            histView.setText("Пока пусто. Запусти тест.");
            return;
        }
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = lines.length - 1; i >= 0 && shown < 10; i--) {
            String[] f = lines[i].split("\t");
            if (f.length < 6) continue;
            sb.append(f[0]).append(" · ").append(f[1]).append(" · ").append(f[2])
                    .append("\n   ").append(f[3]).append(" мс, джиттер ").append(f[4])
                    .append(", потери ").append(f[5]).append("%\n");
            shown++;
        }
        histView.setText(sb.toString().trim());
    }

    void shareResult() {
        if (lastResult.isEmpty()) {
            Toast.makeText(this, "Сначала запусти тест", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "Тест сети для MLBB\n\n" + lastResult);
        startActivity(Intent.createChooser(i, "Поделиться"));
    }

    void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            Toast.makeText(this, "Эти настройки не открываются на этом телефоне", Toast.LENGTH_SHORT).show();
        }
    }

    Button smallBtn(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(ACCENT);
        b.setBackground(rounded(BG, LINE, 1));
        b.setOnClickListener(l);
        return b;
    }

    // ---------- UI ----------

    void render() {
        List<Node> sorted = new ArrayList<>(nodes);
        Collections.sort(sorted, new Comparator<Node>() {
            @Override
            public int compare(Node a, Node b) {
                return rank(a) - rank(b);
            }

            int rank(Node n) {
                if (n.ping >= 0) return n.ping;
                return n.ping == -2 ? 100000 : 200000;
            }
        });

        Node best = (!sorted.isEmpty() && sorted.get(0).ping >= 0) ? sorted.get(0) : null;
        bestLine.setText(best == null ? "Замеряю…"
                : "Лучший: " + best.name + " · " + best.ping + " мс");

        cards.removeAllViews();
        for (int k = 0; k < sorted.size(); k++) {
            final Node n = sorted.get(k);
            boolean sel = n.host.equals(selHost);
            int col = n.ping < 0 ? MUTED : n.ping < 100 ? GOOD : n.ping < 160 ? WARN : BAD;
            String val = n.ping == -2 ? "замер…" : n.ping == -1 ? "нет ответа" : n.ping + " мс";

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(12), dp(16), dp(12));
            card.setBackground(rounded(SURFACE, sel ? ACCENT : LINE, sel ? 2 : 1));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selHost = n.host;
                    prefs.edit().putString("sel", selHost).apply();
                    render();
                }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    deleteDialog(n);
                    return true;
                }
            });

            boolean star = best != null && n == best;
            card.addView(text((star ? "★ " : "") + n.name, 17, FG, true));
            card.addView(text(n.hub + " · " + n.host, 12, MUTED, false));
            TextView p = text(val, 20, col, true);
            p.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            p.setPadding(0, dp(4), 0, 0);
            card.addView(p);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            cards.addView(card, lp);
        }
    }

    void addDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText name = new EditText(this);
        name.setHint("Название, например Астана");
        final EditText host = new EditText(this);
        host.setHint("1.2.3.4  или  1.2.3.4:5555  или  ping:1.2.3.4");
        host.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(name);
        box.addView(host);
        new AlertDialog.Builder(this)
                .setTitle("Новый узел")
                .setView(box)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Добавить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String h = host.getText().toString().trim();
                        String nm = name.getText().toString().trim();
                        if (h.isEmpty()) return;
                        Node n = new Node();
                        n.name = nm.isEmpty() ? h : nm;
                        n.hub = "свой узел";
                        n.host = h;
                        nodes.add(n);
                        saveNodes();
                        render();
                    }
                }).show();
    }

    void deleteDialog(final Node n) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить «" + n.name + "»?")
                .setNegativeButton("Нет", null)
                .setPositiveButton("Удалить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        nodes.remove(n);
                        saveNodes();
                        render();
                    }
                }).show();
    }

    // ---------- хранение ----------

    void loadNodes() {
        nodes.clear();
        String s = prefs.getString("nodes2", null);
        if (s == null || s.trim().isEmpty()) s = DEFAULTS;
        for (String line : s.split("\n")) {
            String[] f = line.split("\t");
            if (f.length < 3) continue;
            Node n = new Node();
            n.name = f[0];
            n.hub = f[1];
            n.host = f[2];
            nodes.add(n);
        }
    }

    void saveNodes() {
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            sb.append(n.name).append('\t').append(n.hub).append('\t').append(n.host).append('\n');
        }
        prefs.edit().putString("nodes2", sb.toString()).apply();
    }

    // ---------- запуск игры ----------

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

    // ---------- хелперы ----------

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
