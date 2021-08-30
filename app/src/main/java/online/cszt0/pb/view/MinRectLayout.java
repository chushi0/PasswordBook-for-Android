package online.cszt0.pb.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MinRectLayout extends FrameLayout {
    private final Paint mPaint;

    public MinRectLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getValueOrInfinity(widthMeasureSpec);
        int height = getValueOrInfinity(heightMeasureSpec);
        int value = Math.min(width, height);

        setMeasuredDimension(value, value);
        int measureSpec = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(0).measure(measureSpec, measureSpec);
        }
    }

    private int getValueOrInfinity(int spec) {
        int mode = MeasureSpec.getMode(spec);
        int value = MeasureSpec.getSize(spec);

        if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) {
            return value;
        }
        return Integer.MAX_VALUE;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
    }
}
