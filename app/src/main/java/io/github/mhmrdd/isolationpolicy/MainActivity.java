package io.github.mhmrdd.isolationpolicy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class MainActivity extends Activity {

    private static final String PREFS = "policy";
    private static final String KEY_DENIED = "denied";

    private TextView mStatus;
    private EditText mSearch;
    private LinearLayout mList;
    private Button mApply;

    private PolicyClient mClient;
    private final Set<String> mDenied = new TreeSet<String>();
    private final List<ApplicationInfo> mApps = new ArrayList<ApplicationInfo>();
    private final Set<String> mExempt = new HashSet<String>();
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private String mFilter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());

        loadDenied();
        scanApps();
        renderList();
        refreshStatus();

        mClient = new PolicyClient(getApplicationContext());
        mClient.start(new ClientReady());
        mUi.postDelayed(new RefreshStatus(), 1500);
    }

    @Override
    protected void onDestroy() {
        if (mClient != null) mClient.stop();
        super.onDestroy();
    }

    private void loadDenied() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        mDenied.clear();
        Set<String> stored = sp.getStringSet(KEY_DENIED, null);
        if (stored != null) mDenied.addAll(stored);
    }

    private void saveDenied() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putStringSet(KEY_DENIED, new HashSet<String>(mDenied))
                .apply();
    }

    private void scanApps() {
        mApps.clear();
        mExempt.clear();
        PackageManager pm = getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            boolean exempt = ai.uid < 10000
                    || ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0)
                    || Constants.APPLICATION_ID.equals(ai.packageName);
            if (exempt) mExempt.add(ai.packageName);
            mApps.add(ai);
        }
        Collections.sort(mApps, new EligibleFirstByLabel(pm));
    }

    private void renderList() {
        mList.removeAllViews();
        PackageManager pm = getPackageManager();
        String f = mFilter.toLowerCase();
        for (ApplicationInfo ai : mApps) {
            CharSequence label;
            try { label = ai.loadLabel(pm); } catch (Throwable t) { label = ai.packageName; }
            if (!f.isEmpty()) {
                String lp = ai.packageName.toLowerCase();
                String ll = label.toString().toLowerCase();
                if (!lp.contains(f) && !ll.contains(f)) continue;
            }
            mList.addView(buildRow(ai, label, pm));
        }
    }

    private View buildRow(ApplicationInfo ai, CharSequence label, PackageManager pm) {
        boolean exempt = mExempt.contains(ai.packageName);
        int active = Color.parseColor("#FFE8E8E8");
        int dim = Color.parseColor("#FF707070");
        int sub = Color.parseColor("#FFA0A0A0");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));

        ImageView icon = new ImageView(this);
        Drawable d = null;
        try { d = ai.loadIcon(pm); } catch (Throwable ignored) {}
        if (d != null) icon.setImageDrawable(d);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40));
        ip.rightMargin = dp(10);
        row.addView(icon, ip);

        LinearLayout vbox = new LinearLayout(this);
        vbox.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(15f);
        title.setTextColor(exempt ? dim : active);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        TextView pkg = new TextView(this);
        pkg.setText(ai.packageName);
        pkg.setTextSize(12f);
        pkg.setTextColor(exempt ? dim : sub);
        pkg.setTypeface(Typeface.MONOSPACE);
        vbox.addView(title);
        vbox.addView(pkg);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(vbox, vp);

        CheckBox cb = new CheckBox(this);
        cb.setChecked(!exempt && mDenied.contains(ai.packageName));
        cb.setEnabled(!exempt);
        cb.setTag(ai.packageName);
        cb.setOnCheckedChangeListener(new CheckListener());
        row.addView(cb);

        if (!exempt) {
            row.setOnClickListener(new RowClickProxy(cb));
        }
        return row;
    }

    private void refreshStatus() {
        int eligible = mApps.size() - mExempt.size();
        boolean ready = mClient != null && mClient.isReady() && mClient.ping();
        StringBuilder sb = new StringBuilder();
        if (ready) {
            long ver = mClient.getVersion();
            int srvCount = mClient.getPolicy().size();
            sb.append("module : active  v=").append(ver).append("  server=").append(srvCount).append('\n');
        } else {
            sb.append("module : checking ...\n");
        }
        sb.append("eligible: ").append(eligible).append("    selected: ").append(mDenied.size());
        mStatus.setText(sb.toString());
        if (mApply != null) mApply.setEnabled(ready);
    }

    private View buildLayout() {
        int gray = Color.parseColor("#FF1E1E1E");
        int faint = Color.parseColor("#FF2C2C2C");
        int textC = Color.parseColor("#FFE8E8E8");
        int sub = Color.parseColor("#FFB0B0B0");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(gray);
        int pad = dp(10);
        root.setPadding(pad, pad, pad, pad);

        mStatus = new TextView(this);
        mStatus.setTypeface(Typeface.MONOSPACE);
        mStatus.setTextColor(textC);
        mStatus.setTextSize(13f);
        mStatus.setBackgroundColor(faint);
        mStatus.setPadding(dp(10), dp(8), dp(10), dp(8));
        mStatus.setTextIsSelectable(true);
        root.addView(mStatus, new LinearLayout.LayoutParams(MATCH, WRAP));

        mSearch = new EditText(this);
        mSearch.setHint("filter by name or package");
        mSearch.setTextColor(textC);
        mSearch.setHintTextColor(sub);
        mSearch.setTextSize(13f);
        mSearch.setSingleLine(true);
        mSearch.setBackgroundColor(faint);
        mSearch.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, WRAP);
        sp.topMargin = dp(8);
        root.addView(mSearch, sp);
        mSearch.addTextChangedListener(new SearchWatcher());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(faint);
        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mList, new ViewGroup.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        lp.topMargin = dp(8);
        root.addView(scroll, lp);

        mApply = new Button(this);
        mApply.setText("Apply changes");
        mApply.setEnabled(false);
        mApply.setOnClickListener(new ApplyClick());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(MATCH, WRAP);
        bp.topMargin = dp(8);
        root.addView(mApply, bp);

        return root;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private class EligibleFirstByLabel implements java.util.Comparator<ApplicationInfo> {
        private final PackageManager mPm;
        EligibleFirstByLabel(PackageManager pm) { this.mPm = pm; }
        @Override
        public int compare(ApplicationInfo a, ApplicationInfo b) {
            boolean ea = mExempt.contains(a.packageName);
            boolean eb = mExempt.contains(b.packageName);
            if (ea != eb) return ea ? 1 : -1;
            String la, lb;
            try { la = a.loadLabel(mPm).toString(); } catch (Throwable t) { la = a.packageName; }
            try { lb = b.loadLabel(mPm).toString(); } catch (Throwable t) { lb = b.packageName; }
            int byLabel = la.compareToIgnoreCase(lb);
            if (byLabel != 0) return byLabel;
            return a.packageName.compareTo(b.packageName);
        }
    }

    private class CheckListener implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton btn, boolean on) {
            String pkg = (String) btn.getTag();
            if (pkg == null) return;
            if (on) mDenied.add(pkg); else mDenied.remove(pkg);
            saveDenied();
            refreshStatus();
        }
    }

    private static class RowClickProxy implements View.OnClickListener {
        private final CheckBox mCb;
        RowClickProxy(CheckBox cb) { this.mCb = cb; }
        @Override
        public void onClick(View v) {
            mCb.setChecked(!mCb.isChecked());
        }
    }

    private class ApplyClick implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (mClient == null) return;
            int n = mClient.setPolicy(new HashSet<String>(mDenied));
            mStatus.append("\npushed: " + n + " entries");
        }
    }

    private class SearchWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mFilter = s.toString();
            renderList();
        }
    }

    private class ClientReady implements PolicyClient.OnReady {
        @Override
        public void onReady(boolean injected) {
            mUi.post(new RefreshStatus());
        }
    }

    private class RefreshStatus implements Runnable {
        @Override
        public void run() {
            refreshStatus();
        }
    }
}
