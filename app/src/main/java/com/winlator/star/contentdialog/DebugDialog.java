package com.winlator.star.contentdialog;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;

import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.winlator.star.R;
import com.winlator.star.core.AppUtils;
import com.winlator.star.core.Callback;
import com.winlator.star.core.UnitUtils;
import com.winlator.star.widget.LogView;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class DebugDialog extends ContentDialog implements Callback<String> {
    private final LogView logView;
    private static boolean paused = false;
    private BufferedWriter writer;

    public DebugDialog(@NonNull Context context) {
        super(context, R.layout.debug_dialog);
        setIcon(R.drawable.icon_debug);
        setTitle(context.getString(R.string.logs));
        logView = findViewById(R.id.LogView);
        
        logView.getLayoutParams().width = (int)UnitUtils.dpToPx(UnitUtils.pxToDp(AppUtils.getScreenWidth()) * 0.7f);

        findViewById(R.id.BTCancel).setVisibility(View.GONE);

        LinearLayout llBottomBarPanel = findViewById(R.id.LLBottomBarPanel);
        llBottomBarPanel.setVisibility(View.VISIBLE);

        View toolbarView = LayoutInflater.from(context).inflate(R.layout.debug_toolbar, llBottomBarPanel, false);
        toolbarView.findViewById(R.id.BTClear).setOnClickListener((v) -> logView.clear());
        toolbarView.findViewById(R.id.BTCopy).setOnClickListener((v) -> copyAllLogs(context));
        toolbarView.findViewById(R.id.BTCopySelected).setOnClickListener((v) -> copySelectedLog(context));
        toolbarView.findViewById(R.id.BTSearchPrevious).setOnClickListener((v) -> logView.findPrevious());
        toolbarView.findViewById(R.id.BTSearchNext).setOnClickListener((v) -> logView.findNext());
        toolbarView.findViewById(R.id.BTPause).setOnClickListener((v) -> {
            setPaused(!paused);
            ((ImageButton)v).setImageResource(getPaused() ? R.drawable.icon_play : R.drawable.icon_pause);
        });
        EditText etSearchLogs = toolbarView.findViewById(R.id.ETSearchLogs);
        etSearchLogs.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                logView.setSearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        llBottomBarPanel.addView(toolbarView);
        try {
            writer = new BufferedWriter(new FileWriter(logView.getLogFile(context)));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void call(final String line) {
        if (!getPaused()) logView.append(line+"\n");
        try {
            writer.write(line + "\n");
            writer.flush();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void copyAllLogs(Context context) {
        ClipboardManager clipboardManager = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText(context.getString(R.string.logs), logView.getContent());
        clipboardManager.setPrimaryClip(clipData);
        AppUtils.showToast(context, R.string.logs_copied);
    }

    private void copySelectedLog(Context context) {
        String selectedContent = logView.getSelectedContent();
        if (selectedContent.isEmpty()) {
            AppUtils.showToast(context, R.string.no_log_selected);
            return;
        }

        ClipboardManager clipboardManager = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText(context.getString(R.string.copy_selected_log), selectedContent);
        clipboardManager.setPrimaryClip(clipData);
        AppUtils.showToast(context, R.string.selected_log_copied);
    }

    public static void setPaused(boolean cond) {
        paused = cond;
    }
    
    public static boolean getPaused() {
        return paused;
    }
}
