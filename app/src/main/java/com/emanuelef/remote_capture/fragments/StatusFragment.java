/*
 * This file is part of PCAPdroid.
 *
 * PCAPdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PCAPdroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PCAPdroid.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2020 - Emanuele Faranda
 */

package com.emanuelef.remote_capture.fragments;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.emanuelef.remote_capture.activities.InspectorActivity;
import com.emanuelef.remote_capture.model.AppState;
import com.emanuelef.remote_capture.CaptureService;
import com.emanuelef.remote_capture.model.Prefs;
import com.emanuelef.remote_capture.R;
import com.emanuelef.remote_capture.Utils;
import com.emanuelef.remote_capture.activities.MainActivity;
import com.emanuelef.remote_capture.activities.StatsActivity;
import com.emanuelef.remote_capture.interfaces.AppStateListener;
import com.emanuelef.remote_capture.model.VPNStats;

public class StatusFragment extends Fragment implements AppStateListener {
    private TextView mCollectorInfo;
    private TextView mCaptureStatus;
    private TextView mInspectorLink;
    private MainActivity mActivity;
    private SharedPreferences mPrefs;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        mActivity = (MainActivity) context;
    }

    @Override
    public void onDestroy() {
        mActivity.setAppStateListener(null);
        mActivity = null;
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.status, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mCollectorInfo = view.findViewById(R.id.collector_info);
        mCaptureStatus = view.findViewById(R.id.status_view);
        mInspectorLink = view.findViewById(R.id.inspector_link);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mActivity);

        mCaptureStatus.setOnClickListener(v -> {
            if(mActivity.getState() == AppState.running) {
                Intent intent = new Intent(getActivity(), StatsActivity.class);
                startActivity(intent);
            }
        });

        // Make URLs clickable
        mCollectorInfo.setMovementMethod(LinkMovementMethod.getInstance());

        setupInspectorLinK();

        mPrefs.registerOnSharedPreferenceChangeListener((sharedPreferences, s) -> {
            if((mActivity != null) && (mActivity.getState() == AppState.ready))
                refreshPcapDumpInfo();
        });

        LocalBroadcastManager bcast_man = LocalBroadcastManager.getInstance(mActivity);

        /* Register for stats update */
        bcast_man.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                processStatsUpdateIntent(intent);
            }
        }, new IntentFilter(CaptureService.ACTION_STATS_DUMP));

        /* Important: call this after all the fields have been initialized */
        mActivity.setAppStateListener(this);
    }

    private void setupInspectorLinK() {
        int color = ContextCompat.getColor(mActivity, android.R.color.tab_indicator_text);

        mInspectorLink.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        Drawable []drawables = mInspectorLink.getCompoundDrawables();
        drawables[0].setColorFilter(color, PorterDuff.Mode.SRC_IN);

        mInspectorLink.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), InspectorActivity.class);
            startActivity(intent);
        });
    }

    private void processStatsUpdateIntent(Intent intent) {
        VPNStats stats = (VPNStats) intent.getSerializableExtra("value");

        Log.d("MainReceiver", "Got StatsUpdate: bytes_sent=" + stats.pkts_sent + ", bytes_rcvd=" +
                stats.bytes_rcvd + ", pkts_sent=" + stats.pkts_sent + ", pkts_rcvd=" + stats.pkts_rcvd);

        mCaptureStatus.setText(Utils.formatBytes(stats.bytes_sent + stats.bytes_rcvd));
    }

    private void refreshPcapDumpInfo() {
        String info;
        String modeName;

        Prefs.DumpMode mode = CaptureService.isServiceActive() ? CaptureService.getDumpMode() : Prefs.getDumpMode(mPrefs);

        switch (mode) {
        case HTTP_SERVER:
            modeName = getResources().getString(R.string.http_server);
            info = String.format(getResources().getString(R.string.http_server_status),
                    Utils.getLocalIPAddress(mActivity), CaptureService.getHTTPServerPort());
            break;
        case PCAP_FILE:
            modeName = getResources().getString(R.string.pcap_file);
            info = "";
            break;
        case UDP_EXPORTER:
            modeName = getResources().getString(R.string.udp_exporter);
            info = String.format(getResources().getString(R.string.collector_info),
                    CaptureService.getCollectorAddress(), CaptureService.getCollectorPort());
            break;
        default:
            modeName = getResources().getString(R.string.no_dump);
            info = "";
            break;
        }

        if(!CaptureService.isServiceActive()) {
            info = getResources().getString(R.string.dump_mode) + ": " + modeName;

            if(Prefs.getTlsDecryptionEnabled(mPrefs))
                info += " (" + getResources().getString(R.string.with_tls_decryption) + ")";
        }

        mCollectorInfo.setText(info);
    }

    @Override
    public void appStateChanged(AppState state) {
        switch(state) {
            case ready:
                mCaptureStatus.setText(R.string.ready);
                mInspectorLink.setVisibility(View.GONE);
                refreshPcapDumpInfo();
                break;
            case running:
                mCaptureStatus.setText(Utils.formatBytes(CaptureService.getBytes()));
                mInspectorLink.setVisibility(View.VISIBLE);
                refreshPcapDumpInfo();
                break;
            default:
                break;
        }
    }
}
