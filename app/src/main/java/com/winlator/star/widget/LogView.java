package com.winlator.star.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.winlator.star.R;
import com.winlator.star.SettingsFragment;
import com.winlator.star.contentdialog.DebugDialog;
import com.winlator.star.core.FileUtils;
import com.winlator.star.core.UnitUtils;
import com.winlator.star.math.Mathf;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class LogView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<String> lines = new ArrayList<>();
    private final ArrayList<Integer> searchMatches = new ArrayList<>();
    private final float rowHeight = UnitUtils.dpToPx(30);
    private final float defaultTextSize = UnitUtils.dpToPx(16);
    private final float minScrollThumbSize = UnitUtils.dpToPx(6);
    private final float channelStripeWidth = UnitUtils.dpToPx(4);
    private final PointF lastPoint = new PointF();
    private final PointF downPoint = new PointF();
    private final PointF scrollPosition = new PointF();
    private final PointF scrollSize = new PointF();
    private boolean isActionDown = false;
    private static String fileName;
    private boolean scrollingHorizontally = false;
    private boolean scrollingVertically = false;
    private int selectedLineIndex = -1;
    private int currentSearchMatch = -1;
    private String searchQuery = "";
    private final Object lock = new Object();

    public LogView(Context context) {
        this(context, null);
    }

    public LogView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LogView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public LogView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeScrollSize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0) return;
        
        synchronized (lock) {
            paint.setStyle(Paint.Style.FILL);

            if (lines.isEmpty()) {
                paint.setTextSize(UnitUtils.dpToPx(20));
                paint.setColor(0xffbdbdbd);
                String text = getContext().getString(R.string.no_items_to_display);
                float centerX = (width - paint.measureText(text)) * 0.5f;
                float centerY = (height - paint.getFontSpacing()) * 0.5f - paint.ascent();
                canvas.drawText(text, centerX, centerY, paint);
                return;
            }

            paint.setTextSize(defaultTextSize);
            float textHeight = paint.getFontSpacing();

            float rowY = -scrollPosition.y;
            
            
            for (int i = 0, count = lines.size(); i < count; i++) {
                if ((rowY + rowHeight) < 0 || rowY >= height) {
                    rowY += rowHeight;
                    continue;
                }

                boolean selected = i == selectedLineIndex;
                boolean currentMatch = currentSearchMatch >= 0 && currentSearchMatch < searchMatches.size() && searchMatches.get(currentSearchMatch) == i;
                boolean searchMatch = searchMatches.contains(i);

                if (selected) paint.setColor(0xffbbdefb);
                else if (currentMatch) paint.setColor(0xfffff59d);
                else if (searchMatch) paint.setColor(0xfffff9c4);
                else paint.setColor((i % 2) != 0 ? 0xffeaf6fb : 0xffffffff);
                canvas.drawRect(-scrollPosition.x, rowY, width, rowY + rowHeight, paint);

                paint.setColor(getChannelColor(lines.get(i)));
                canvas.drawRect(-scrollPosition.x, rowY, -scrollPosition.x + channelStripeWidth, rowY + rowHeight, paint);

                float centerY = (rowY - paint.ascent()) + (rowHeight - textHeight) * 0.5f;
                drawLogLine(canvas, lines.get(i), -scrollPosition.x + channelStripeWidth + UnitUtils.dpToPx(6), centerY, searchMatch);
                rowY += rowHeight;
            }
             
            drawScrollThumbs(canvas);
        }
    }

    private void drawLogLine(Canvas canvas, String line, float x, float y, boolean highlightSearch) {
        if (!highlightSearch || searchQuery.isEmpty()) {
            paint.setColor(getChannelColor(line));
            canvas.drawText(line, x, y, paint);
            return;
        }

        String lowerLine = line.toLowerCase(Locale.US);
        String lowerQuery = searchQuery.toLowerCase(Locale.US);
        int matchStart = lowerLine.indexOf(lowerQuery);
        if (matchStart < 0) {
            paint.setColor(getChannelColor(line));
            canvas.drawText(line, x, y, paint);
            return;
        }

        int matchEnd = matchStart + searchQuery.length();
        String before = line.substring(0, matchStart);
        String match = line.substring(matchStart, matchEnd);
        String after = line.substring(matchEnd);
        float beforeWidth = paint.measureText(before);
        float matchWidth = paint.measureText(match);

        paint.setColor(0x99ffeb3b);
        canvas.drawRect(x + beforeWidth, y + paint.ascent(), x + beforeWidth + matchWidth, y + paint.descent(), paint);

        paint.setColor(getChannelColor(line));
        canvas.drawText(before, x, y, paint);
        canvas.drawText(match, x + beforeWidth, y, paint);
        canvas.drawText(after, x + beforeWidth + matchWidth, y, paint);
    }

    private int getChannelColor(String line) {
        String lowerLine = line.toLowerCase(Locale.US);
        if (lowerLine.contains("err:") || lowerLine.contains("error")) return 0xffb71c1c;
        if (lowerLine.contains("warn:") || lowerLine.contains("warning")) return 0xffef6c00;
        if (lowerLine.contains("fixme:")) return 0xff6a1b9a;
        if (lowerLine.contains("trace:")) return 0xff1565c0;
        if (lowerLine.contains("box64") || lowerLine.contains("box86")) return 0xff2e7d32;
        if (lowerLine.contains("x11") || lowerLine.contains("xserver")) return 0xff00838f;
        if (lowerLine.contains("wine")) return 0xff283593;
        return 0xff212121;
    }

    private void drawScrollThumbs(Canvas canvas) {
        float scrollThumbX = getScrollThumbX();
        float scrollThumbY = getScrollThumbY();
        float scrollThumbWidth = getScrollThumbWidth();
        float scrollThumbHeight = getScrollThumbHeight();

        paint.setColor(0x33000000);
        float radius = minScrollThumbSize * 0.5f;

        canvas.drawRoundRect(scrollThumbX, getHeight() - minScrollThumbSize, scrollThumbX + scrollThumbWidth, getHeight(), radius, radius, paint);
        canvas.drawRoundRect(getWidth() - minScrollThumbSize, scrollThumbY, getWidth(), scrollThumbY + scrollThumbHeight, radius, radius, paint);
    }

    public float getScrollMaxLeft() {
        return Math.max(0, scrollSize.x - getWidth());
    }

    public float getScrollMaxTop() {
        return Math.max(0, scrollSize.y - getHeight());
    }

    public float getScrollThumbX() {
        float width = getWidth();
        if (scrollSize.x > 0 && scrollSize.x > width) return scrollPosition.x * (width / scrollSize.x);
        return -Float.MAX_VALUE;
    }

    public float getScrollThumbY() {
        float height = getHeight();
        if (scrollSize.y > 0 && scrollSize.y > height) return scrollPosition.y * (height / scrollSize.y);
        return -Float.MAX_VALUE;
    }

    public float getScrollThumbWidth() {
        float width = getWidth();
        if (scrollSize.x > 0 && scrollSize.x > width) {
            return Math.max(width - width * (getScrollMaxLeft() / scrollSize.x), minScrollThumbSize);
        }
        return 0;
    }

    public float getScrollThumbHeight() {
        float height = getHeight();
        if (scrollSize.y > 0 && scrollSize.y > height) {
            return Math.max(height - height * (getScrollMaxTop() / scrollSize.y), minScrollThumbSize);
        }
        return 0;
    }

    private void computeScrollSize() {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float maxWidth = 0;
        paint.setTextSize(defaultTextSize);
        for (int i = 0, count = lines.size(); i < count; i++) maxWidth = Math.max(paint.measureText(lines.get(i)), maxWidth);
        scrollSize.x = Math.max(maxWidth, width);
        scrollSize.y = Math.max(rowHeight * lines.size(), height);
        scrollPosition.set(0, getScrollMaxTop());
    }

    public void clear() {
        synchronized (lock) {
            lines.clear();
            searchMatches.clear();
            selectedLineIndex = -1;
            currentSearchMatch = -1;
        }
        postInvalidate();
    }

    public void append(String line) {
        synchronized (lock) {
            lines.add("["+DateFormat.format("HH:mm:ss", System.currentTimeMillis())+"]  "+line.replace("\n", ""));
            computeScrollSize();
            computeSearchMatches();
        }
    }

    public String getContent() {
        synchronized (lock) {
            return String.join("\n", lines);
        }
    }

    public String getSelectedContent() {
        synchronized (lock) {
            if (selectedLineIndex < 0 || selectedLineIndex >= lines.size()) return "";
            return lines.get(selectedLineIndex);
        }
    }

    public void setSearchQuery(String query) {
        synchronized (lock) {
            searchQuery = query != null ? query.trim() : "";
            computeSearchMatches();
            currentSearchMatch = searchMatches.isEmpty() ? -1 : 0;
            scrollToCurrentSearchMatch();
        }
        postInvalidate();
    }

    public void findNext() {
        synchronized (lock) {
            if (searchMatches.isEmpty()) return;
            currentSearchMatch = (currentSearchMatch + 1) % searchMatches.size();
            scrollToCurrentSearchMatch();
        }
        postInvalidate();
    }

    public void findPrevious() {
        synchronized (lock) {
            if (searchMatches.isEmpty()) return;
            currentSearchMatch = currentSearchMatch <= 0 ? searchMatches.size() - 1 : currentSearchMatch - 1;
            scrollToCurrentSearchMatch();
        }
        postInvalidate();
    }

    private void computeSearchMatches() {
        searchMatches.clear();
        if (searchQuery.isEmpty()) return;

        String lowerQuery = searchQuery.toLowerCase(Locale.US);
        for (int i = 0, count = lines.size(); i < count; i++) {
            if (lines.get(i).toLowerCase(Locale.US).contains(lowerQuery)) searchMatches.add(i);
        }
    }

    private void scrollToCurrentSearchMatch() {
        if (currentSearchMatch < 0 || currentSearchMatch >= searchMatches.size()) return;
        int lineIndex = searchMatches.get(currentSearchMatch);
        scrollPosition.y = Mathf.clamp(lineIndex * rowHeight - rowHeight, 0, getScrollMaxTop());
    }

    private int getLineIndexAt(float y) {
        return (int)((y + scrollPosition.y) / rowHeight);
    }

    public static void setFilename(String file) {
        fileName = file.substring(0, file.lastIndexOf("."));
    }

    public static File getLogFile(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String winlatorPath = sp.getString("winlator_path_uri", null);
        File logsDir;

        if (winlatorPath != null) {
            Uri winlatorUri = Uri.parse(winlatorPath);
            logsDir = new File(FileUtils.getFilePathFromUri(context, winlatorUri), "logs");
        }
        else {
            logsDir = new File(SettingsFragment.DEFAULT_WINLATOR_PATH, "logs");
        }

        if (!logsDir.exists())
            logsDir.mkdirs();

        String logFile = fileName.replaceAll("\\s", "_").toLowerCase() + "_" + DateFormat.format("yyyy-MM-dd_HH-mm-ss", new Date()) + ".txt";
        return new File(logsDir, logFile);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastPoint.set(event.getX(), event.getY());
                downPoint.set(event.getX(), event.getY());
                isActionDown = true;
                scrollingHorizontally = false;
                scrollingVertically = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (isActionDown) {
                    float dx = event.getX() - lastPoint.x;
                    float dy = event.getY() - lastPoint.y;

                    if (Math.abs(dx) > 10) scrollingHorizontally = true;
                    if (Math.abs(dy) > 10) scrollingVertically = true;

                    if (scrollingHorizontally) {
                        DebugDialog.setPaused(true);
                        scrollPosition.x = Mathf.clamp(scrollPosition.x - dx, 0, getScrollMaxLeft());
                        lastPoint.set(event.getX(), event.getY());
                        invalidate();
                    }

                    if (scrollingVertically) {
                        DebugDialog.setPaused(true);
                        scrollPosition.y = Mathf.clamp(scrollPosition.y - dy, 0, getScrollMaxTop());
                        lastPoint.set(event.getX(), event.getY());
                        invalidate();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!scrollingHorizontally && !scrollingVertically && Math.abs(event.getX() - downPoint.x) < 10 && Math.abs(event.getY() - downPoint.y) < 10) {
                    synchronized (lock) {
                        int lineIndex = getLineIndexAt(event.getY());
                        selectedLineIndex = lineIndex >= 0 && lineIndex < lines.size() ? lineIndex : -1;
                    }
                    invalidate();
                }
                DebugDialog.setPaused(false);
                isActionDown = false;
                break;
        }

        return true;
    }
}
