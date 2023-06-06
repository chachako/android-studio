package android.com.java.profilertester.fragment;

import android.com.java.profilertester.R;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

/**
 * An empty activity whose purpose is to own and navigate between multiple fragments, for testing
 * the profiling of fragments.
 */
public class FragmentHostActivity extends AppCompatActivity
        implements NavigateToNextFragmentListener {

    // This array of Fragments is causing a leak, but it's known and intentional.
    Fragment[] fragments =
            new Fragment[] {
                new FragmentA(), new FragmentB(),
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_host);

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.add(R.id.fragment_container, fragments[0]);
        transaction.commit();

        startUpdatingIntervalLog();
    }

    @Override
    public void onNavigateRequested() {
        toggleActiveFragment();
    }

    private void toggleActiveFragment() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment toHide = fm.getFragments().get(0);
        Fragment toShow = (fragments[0] == toHide) ? fragments[1] : fragments[0];

        FragmentTransaction transaction = fm.beginTransaction();
        transaction.replace(R.id.fragment_container, toShow);
        transaction.commit();
    }

    private void startUpdatingIntervalLog() {
        final long EXPECTED_INTERVAL_MS = 100;
        final TextView logView = findViewById(R.id.update_interval_log);
        logView.setMovementMethod(new ScrollingMovementMethod());
        final Handler handler = new Handler();
        final long[] timestampMs = new long[1];
        timestampMs[0] = 0;

        handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        long currentMs = System.currentTimeMillis();
                        if (timestampMs[0] != 0) {
                            long durationMs = currentMs - timestampMs[0];
                            StringBuilder sb = new StringBuilder();
                            if (durationMs >= EXPECTED_INTERVAL_MS * 2) {
                                sb.append(">>>");
                            }
                            sb.append(durationMs);
                            if (durationMs >= EXPECTED_INTERVAL_MS * 2) {
                                sb.append("<<<");
                            }
                            sb.append("  ");
                            logView.append(sb.toString());
                        }
                        timestampMs[0] = currentMs;
                        // Post the code again with a delay.
                        handler.postDelayed(
                                this,
                                EXPECTED_INTERVAL_MS - (System.currentTimeMillis() - currentMs));
                    }
                });
    }
}
