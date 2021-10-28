/*
 * Copyright 2017 GcsSloop
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Last modified 2017-09-20 16:32:43
 *
 * GitHub: https://github.com/GcsSloop
 * WeiBo: http://weibo.com/GcsSloop
 * WebSite: http://www.gcssloop.com
 */

package com.gcssloop.widget;

import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.recyclerview.widget.RecyclerView;

import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;


/**
 * 作用：分页的网格布局管理器
 * 作者：GcsSloop
 * 摘要：
 * 1. 网格布局
 * 2. 支持水平分页和垂直分页
 * 3. 杜绝高内存占用
 */
public class PagerGridLayoutManager extends RecyclerView.LayoutManager
        implements RecyclerView.SmoothScroller.ScrollVectorProvider {
    private static final String TAG = PagerGridLayoutManager.class.getSimpleName();

    public static final int VERTICAL = 0;           // 垂直滚动
    public static final int HORIZONTAL = 1;         // 水平滚动

    @IntDef({VERTICAL, HORIZONTAL})
    public @interface OrientationType {}            // 滚动类型

    @OrientationType
    private int mOrientation = HORIZONTAL;          // 默认水平滚动

    private int mOffsetX = 0;                       // 水平滚动距离(偏移量)
    private int mOffsetY = 0;                       // 垂直滚动距离(偏移量)

    private int mRows = 0;                          // 行数
    private int mColumns = 0;                       // 列数
    private int mOnePageSize = 0;                   // 一页的条目数量

    private SparseArray<Rect> mItemFrames;          // 条目的显示区域

    private int mItemWidth = 0;                     // 条目宽度
    private int mItemHeight = 0;                    // 条目高度

    private int mWidthUsed = 0;                     // 已经使用空间，用于测量View
    private int mHeightUsed = 0;                    // 已经使用空间，用于测量View

    private int mMaxScrollX;                        // 最大允许滑动的宽度
    private int mMaxScrollY;                        // 最大允许滑动的高度
    private int mScrollState = SCROLL_STATE_IDLE;   // 滚动状态

    /**
     * 构造函数
     *
     * @param rows        行数
     * @param columns     列数
     * @param orientation 方向
     */
    public PagerGridLayoutManager(@IntRange(from = 1, to = 100) int rows,
                                  @IntRange(from = 1, to = 100) int columns,
                                  @OrientationType int orientation) {
        mItemFrames = new SparseArray<>();
        mOrientation = orientation;
        mRows = rows;
        mColumns = columns;
        mOnePageSize = mRows * mColumns;
        mCurrentPageIndex = 0;
    }

    //--- 处理布局 ----------------------------------------------------------------------------------

    /**
     * 布局子View
     *
     * @param recycler Recycler
     * @param state    State
     */
    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        Logi("Item onLayoutChildren");
        if (state.isPreLayout()) {
            return;
        }

        if (getItemCount() == 0) {
            removeAndRecycleAllViews(recycler);
            // 页面变化回调
            setPageCount(0);
            setPageIndex(0, false);
            mCurrentPageIndex = 0;
            return;
        }

        // 计算页面数量
        int mPageCount = getItemCount() / mOnePageSize;
        if (getItemCount() % mOnePageSize != 0) {
            mPageCount++;
        }

        // 计算可以滚动的最大数值，并对滚动距离进行修正
        if (canScrollHorizontally()) {
            mMaxScrollX = (mPageCount - 1) * getUsableWidth();
            mMaxScrollY = getUsableHeight();
            if (mOffsetX > mMaxScrollX) {
                mOffsetX = mMaxScrollX;
            }
        } else {
            mMaxScrollX = getUsableWidth();
            mMaxScrollY = (mPageCount - 1) * getUsableHeight();
            if (mOffsetY > mMaxScrollY) {
                mOffsetY = mMaxScrollY;
            }
        }

        if (mCurrentPageIndex >= getTotalPageCount()) {
            mCurrentPageIndex = getTotalPageCount() - 1;
        }
        if (mCurrentPageIndex < 0) {
            mCurrentPageIndex = 0;
        }

        // 接口回调
        // setPageCount(mPageCount);
        // setPageIndex(mCurrentPageIndex, false);

        Logi("count = " + getItemCount());

        if (mItemWidth <= 0) {
            mItemWidth = getUsableWidth() / mColumns;
        }
        if (mItemHeight <= 0) {
            mItemHeight = getUsableHeight() / mRows;
        }

        mWidthUsed = getUsableWidth() / mColumns * (mColumns - 1);
        mHeightUsed = getUsableHeight() / mRows * (mRows - 1);

        // 预存储两页的View显示区域
        for (int i = 0; i < mOnePageSize * 2; i++) {
            getItemFrameByPosition(i);
        }

        // 预存储View
        for (int i = 0; i < mOnePageSize; i++) {
            if (i >= getItemCount()) break; // 防止数据过少时导致数组越界异常
            View view = recycler.getViewForPosition(i);
            addView(view);
            measureChildWithMargins(view, mWidthUsed, mHeightUsed);
        }

        // 回收和填充布局
        recycleAndFillItems(recycler, state);
    }

    /**
     * 布局结束
     *
     * @param state State
     */
    @Override public void onLayoutCompleted(RecyclerView.State state) {
        super.onLayoutCompleted(state);
        if (state.isPreLayout()) return;
        // 页面状态回调
        setPageCount(getTotalPageCount());
        setPageIndex(mCurrentPageIndex, false);
    }

    /**
     * 回收和填充布局
     *
     * @param recycler Recycler
     * @param state    State
     */
    private void recycleAndFillItems(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (state.isPreLayout()) {
            return;
        }

        Logi("mOffsetX = " + mOffsetX);
        Logi("mOffsetY = " + mOffsetY);

        Rect displayRect = new Rect(
                getPaddingLeft() + mOffsetX,
                getPaddingTop() + mOffsetY,
                getWidth() - getPaddingLeft() - getPaddingRight() + mOffsetX,
                getHeight() - getPaddingTop() - getPaddingBottom() + mOffsetY);

        // 对显示区域进行修正，前后多存储一列或则一行
        if (canScrollHorizontally()) {
            // 水平滚动，多存储一列
            displayRect.left = displayRect.left - mItemWidth;
            if (displayRect.left < 0) {
                displayRect.left = 0;
            }
            displayRect.right = displayRect.right + mItemWidth;
            if (displayRect.right > mMaxScrollX + getUsableWidth()) {
                displayRect.right = mMaxScrollX + getUsableWidth();
            }
        } else if (canScrollVertically()) {
            // 垂直滚动，多存储一行
            displayRect.top = displayRect.top - mItemHeight;
            if (displayRect.top < 0) {
                displayRect.top = 0;
            }
            displayRect.bottom = displayRect.bottom + mItemHeight;
            if (displayRect.bottom > mMaxScrollY + getUsableHeight()) {
                displayRect.bottom = mMaxScrollY + getUsableHeight();
            }
        }


        Loge("displayRect = " + displayRect.toString());

        int startPos = 0;                  // 获取第一个条目的Pos
        if (getChildCount() > 0) {
            startPos = getPosition(getChildAt(0));
            startPos = startPos - mOnePageSize * 2;
            if (startPos < 0) {
                startPos = 0;
            }
        }
        int stopPos = startPos + mOnePageSize * 4;
        if (stopPos > getItemCount()) {
            stopPos = getItemCount();
        }

        Loge("startPos = " + startPos);
        Loge("stopPos = " + stopPos);

        detachAndScrapAttachedViews(recycler); // 移除所有View

        for (int i = startPos; i < stopPos; i++) {
            View child = recycler.getViewForPosition(i);
            Rect rect = getItemFrameByPosition(i);
            if (!Rect.intersects(displayRect, rect)) {
                removeAndRecycleView(child, recycler);   // 回收入暂存区
            } else {
                addView(child);
                measureChildWithMargins(child, mWidthUsed, mHeightUsed);
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
                layoutDecorated(child,
                                rect.left - mOffsetX + lp.leftMargin,
                                rect.top - mOffsetY + lp.topMargin,
                                rect.right - mOffsetX - lp.rightMargin,
                                rect.bottom - mOffsetY - lp.bottomMargin);
            }
        }
        Loge("child count = " + getChildCount());
    }

    //--- 处理滚动 ----------------------------------------------------------------------------------

    /**
     * 水平滚动
     *
     * @param dx       滚动距离
     * @param recycler 回收器
     * @param state    滚动状态
     * @return 实际滚动距离
     */
    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State
            state) {
        int newX = mOffsetX + dx;
        int result = dx;
        if (newX > mMaxScrollX) {
            result = mMaxScrollX - mOffsetX;
        } else if (newX < 0) {
            result = 0 - mOffsetX;
        }
        mOffsetX += result;
        setPageChangedByOffset(mOffsetX);
        offsetChildrenHorizontal(-result);
        recycleAndFillItems(recycler, state);
        return result;
    }

    /**
     * 垂直滚动
     *
     * @param dy       滚动距离
     * @param recycler 回收器
     * @param state    滚动状态
     * @return 实际滚动距离
     */
    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State
            state) {
        int newY = mOffsetY + dy;
        int result = dy;
        if (newY > mMaxScrollY) {
            result = mMaxScrollY - mOffsetY;
        } else if (newY < 0) {
            result = 0 - mOffsetY;
        }
        mOffsetY += result;
        setPageChangedByOffset(mOffsetY);
        offsetChildrenVertical(-result);
        recycleAndFillItems(recycler, state);
        return result;
    }

    /**
     * 监听滚动状态，滚动结束后通知当前选中的页面
     *
     * @param state 滚动状态
     */
    @Override public void onScrollStateChanged(int state) {
        Logi("onScrollStateChanged = " + state);
        mScrollState = state;
        super.onScrollStateChanged(state);
        if (state == SCROLL_STATE_IDLE) {
            if (getItemCount() <= 0 || getChildCount() <= 0) {
                mCurrentPageIndex = 0;
            }
            // 计算当前页面
            if (getItemCount() <= 0 || getChildCount() <= 0)
                mCurrentPageIndex = 0;
            // 根据偏移量计算
            int pageIndex = 0;
            if (canScrollHorizontally()) {
                pageIndex = mOffsetX / getUsableWidth();
                if (mOffsetX % getUsableWidth() > (getUsableWidth() / 2)) {
                    pageIndex++;
                }
            } else {
                pageIndex = mOffsetY / getUsableHeight();
                if (mOffsetY % getUsableHeight() > (getUsableHeight() / 2)) {
                    pageIndex++;
                }
            }
            mCurrentPageIndex = pageIndex;
            setPageIndex(mCurrentPageIndex, false);
        }
    }

    /**
     * 获取需要对齐的View
     *
     * @return 需要对齐的View
     */
    public View findSnapView() {
        if (null != getFocusedChild()) {
            return getFocusedChild();
        }
        if (getChildCount() <= 0) {
            return null;
        }
        int pageIndex = mCurrentPageIndex;
        int targetPos = pageIndex * mOnePageSize;   // 目标Pos
        for (int i = 0; i < getChildCount(); i++) {
            int childPos = getPosition(getChildAt(i));
            if (childPos == targetPos) {
                return getChildAt(i);
            }
        }
        return getChildAt(0);
    }

    //--- 私有方法 ----------------------------------------------------------------------------------

    /**
     * 设置回调监听
     *
     * @param offset 偏移量
     */
    private void setPageChangedByOffset(int offset) {
        int pageIndex = -1;
        if (canScrollHorizontally()) {
            pageIndex = offset / getUsableWidth();
        } else if (canScrollVertically()) {
            pageIndex = offset / getUsableHeight();
        }
        if (pageIndex >= 0) {
            setPageIndex(pageIndex, true);
        }
    }

    /**
     * 获取条目显示区域
     *
     * @param pos 位置下标
     * @return 显示区域
     */
    private Rect getItemFrameByPosition(int pos) {
        Rect rect = mItemFrames.get(pos);
        if (null == rect) {
            rect = new Rect();
            // 计算显示区域 Rect

            // 1. 获取当前View所在页数
            int page = pos / mOnePageSize;

            // 2. 计算当前页数左上角的总偏移量
            int offsetX = 0;
            int offsetY = 0;
            if (canScrollHorizontally()) {
                offsetX += getUsableWidth() * page;
            } else {
                offsetY += getUsableHeight() * page;
            }

            // 3. 根据在当前页面中的位置确定具体偏移量
            int pagePos = pos % mOnePageSize;       // 在当前页面中是第几个
            int row = pagePos / mColumns;           // 获取所在行
            int col = pagePos - (row * mColumns);   // 获取所在列

            offsetX += col * mItemWidth;
            offsetY += row * mItemHeight;

            // 状态输出，用于调试
            Logi("pagePos = " + pagePos);
            Logi("行 = " + row);
            Logi("列 = " + col);

            Logi("offsetX = " + offsetX);
            Logi("offsetY = " + offsetY);

            rect.left = offsetX;
            rect.top = offsetY;
            rect.right = offsetX + mItemWidth;
            rect.bottom = offsetY + mItemHeight;

            // 存储
            mItemFrames.put(pos, rect);
        }
        return rect;
    }

    /**
     * 获取可用的宽度
     *
     * @return 宽度 - padding
     */
    private int getUsableWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    /**
     * 获取可用的高度
     *
     * @return 高度 - padding
     */
    private int getUsableHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    //--- 页面相关(私有) -----------------------------------------------------------------------------

    /**
     * 获取总页数
     */
    private int getTotalPageCount() {
        if (getItemCount() <= 0) return 0;
        int totalCount = getItemCount() / mOnePageSize;
        if (getItemCount() % mOnePageSize != 0) {
            totalCount++;
        }
        return totalCount;
    }

    /**
     * 根据pos，获取该View所在的页面
     *
     * @param pos position
     * @return 页面的页码
     */
    private int getPageIndexByPos(int pos) {
        return pos / mOnePageSize;
    }

    //--- 公开方法 ----------------------------------------------------------------------------------

    /**
     * 创建默认布局参数
     *
     * @return 默认布局参数
     */
    @Override public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                             ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 是否可以水平滚动
     *
     * @return true 是，false 不是。
     */
    @Override public boolean canScrollHorizontally() {
        return mOrientation == HORIZONTAL;
    }

    /**
     * 是否可以垂直滚动
     *
     * @return true 是，false 不是。
     */
    @Override public boolean canScrollVertically() {
        return mOrientation == VERTICAL;
    }


    /**
     * 计算到目标位置需要滚动的距离{@link RecyclerView.SmoothScroller.ScrollVectorProvider}
     *
     * @param targetPosition 目标控件
     * @return 需要滚动的距离
     */
    @Override public PointF computeScrollVectorForPosition(int targetPosition) {
        Loge("computeScrollVectorForPosition targetPos = " + targetPosition);
        int[] pos = getPageLeftTopByPosition(targetPosition);
        Loge("computeScrollVectorForPosition pos = " + pos[0] + ":" + pos[1]);
        PointF vector = new PointF();
        vector.x = pos[0] - mOffsetX;
        vector.y = pos[1] - mOffsetY;
        Loge("computeScrollVectorForPosition = " + vector.toString());
        return vector;
    }

    /**
     * 找到下一页第一个条目的位置
     *
     * @return 第一个搞条目的位置
     */
    public int findNextPageFirstPos() {
        int page = mCurrentPageIndex;
        page++;
        if (page >= getTotalPageCount()) {
            page = getTotalPageCount();
        }
        Loge("computeScrollVectorForPosition next = " + page);
        return page * mOnePageSize;
    }

    /**
     * 找到上一页的第一个条目的位置
     *
     * @return 第一个条目的位置
     */
    public int findPrePageFirstPos() {
        // 在获取时由于前一页的View预加载出来了，所以获取到的直接就是前一页
        int page = mCurrentPageIndex;
        Loge("computeScrollVectorForPosition pre = " + page);
        if (page < 0) {
            page = 0;
        }
        Loge("computeScrollVectorForPosition pre = " + page);
        return page * mOnePageSize;
    }

    /**
     * 获取当前 X 轴偏移量
     *
     * @return X 轴偏移量
     */
    public int getOffsetX() {
        return mOffsetX;
    }

    /**
     * 获取当前 Y 轴偏移量
     *
     * @return Y 轴偏移量
     */
    public int getOffsetY() {
        return mOffsetY;
    }

    //--- 获取偏移量 --------------------------------------------------------------------------------

    /**
     * 获取偏移量(为PagerGridSnapHelper准备)
     * 用于分页滚动，确定需要滚动的距离。
     * {@link PagerGridSnapHelper}
     *
     * @param pos 下标
     */
    public int[] getSnapOffset(int pos) {
        int[] offset = new int[2];
        if (getChildCount() <= 0) {
            offset[0] = 0;
            offset[1] = 0;
        } else {
            int[] pageLeftTop = getPageLeftTopByPosition(pos);
            int[] pageCenter = getPageCenterByPosition(pos);

            Loge("getSnapOffset pos = " + pos);
            Loge("pageLeftTop = " + pageLeftTop[0] + ":" + pageLeftTop[1]);
            Loge("pageCenter = " + pageCenter[0] + ":" + pageCenter[1]);
            Loge("offset = " + mOffsetX + ":" + mOffsetY);

            // 水平模式
            if (canScrollHorizontally()) {
                offset[0] = pageLeftTop[0] - mOffsetX;
                offset[1] = 0;
            } else {
                offset[0] = 0;
                offset[1] = pageLeftTop[1] - mOffsetY;
            }

        }
        Logi("findTargetSnapPosition offset = " + offset[0] + ":" + offset[1]);
        return offset;
    }

    /**
     * 根据条目下标获取该条目所在页面的左上角位置
     *
     * @param pos 条目下标
     * @return 左上角位置
     */
    private int[] getPageLeftTopByPosition(int pos) {
        int[] leftTop = new int[2];
        int page = getPageIndexByPos(pos);
        if (canScrollHorizontally()) {
            leftTop[0] = page * getUsableWidth();
            leftTop[1] = 0;
        } else {
            leftTop[0] = 0;
            leftTop[1] = page * getUsableHeight();
        }
        return leftTop;
    }

    /**
     * 根据条目下标获取该条目所在页面的中间位置
     *
     * @param pos 条目下标
     * @return 中间位置
     */
    private int[] getPageCenterByPosition(int pos) {
        int[] center = new int[2];
        int page = pos / mOnePageSize;
        if (pos % mOnePageSize != 0) {
            page++;
        }
        if (canScrollHorizontally()) {
            center[0] = (page - 1) * getUsableWidth() + getUsableWidth() / 2;
            center[1] = getUsableHeight() / 2;
        } else {
            center[0] = getUsableWidth() / 2;
            center[1] = (page - 1) * getUsableHeight() + getUsableHeight() / 2;
        }
        return center;
    }

    //--- 处理页码变化 -------------------------------------------------------------------------------

    private int mCurrentPageIndex = -1;                 // 当前页面下标
    private boolean mChangeSelectInScrolling = true;    // 是否在滚动过程中对页面变化回调
    private int mLastPageCount = -1;                    // 上次页面总数
    private int mLastPageIndex = -1;                    // 上次页面下标

    /**
     * 设置页面总数
     *
     * @param pageCount 页面总数
     */
    private void setPageCount(int pageCount) {
        if (pageCount >= 0 && pageCount != mLastPageCount && mPageListener != null) {
            mPageListener.onPageSizeChanged(pageCount);
            mLastPageCount = pageCount;
        }
    }

    /**
     * 设置当前选中页面
     *
     * @param pageIndex   页面下标
     * @param isScrolling 是否处于滚动状态
     */
    private void setPageIndex(int pageIndex, boolean isScrolling) {
        Loge("setPageIndex = " + pageIndex + ":" + isScrolling);
        if (isScrolling && !mChangeSelectInScrolling) return;
        if (isScrolling && pageIndex == mLastPageIndex) return;
        if (pageIndex >= 0 && mPageListener != null) {
            mPageListener.onPageSelect(pageIndex);
            mCurrentPageIndex = pageIndex;
            mLastPageIndex = pageIndex;
        }
    }

    /**
     * 设置是否在滚动状态更新选中页码
     *
     * @param changeSelectInScrolling true：更新、false：不更新
     */
    public void setChangeSelectInScrolling(boolean changeSelectInScrolling) {
        mChangeSelectInScrolling = changeSelectInScrolling;
    }


    /**
     * 设置滚动方向
     *
     * @param orientation 滚动方向
     * @return 最终的滚动方向
     */
    @OrientationType
    public int setOrientationType(@OrientationType int orientation) {
        if (mOrientation == orientation || mScrollState != SCROLL_STATE_IDLE) return mOrientation;
        mOrientation = orientation;
        mItemFrames.clear();
        int x = mOffsetX;
        int y = mOffsetY;
        mOffsetX = y / getUsableHeight() * getUsableWidth();
        mOffsetY = x / getUsableWidth() * getUsableHeight();
        int mx = mMaxScrollX;
        int my = mMaxScrollY;
        mMaxScrollX = my / getUsableHeight() * getUsableWidth();
        mMaxScrollY = mx / getUsableWidth() * getUsableHeight();
        return mOrientation;
    }

    //--- 对外接口 ----------------------------------------------------------------------------------

    private PageListener mPageListener = null;

    public void setPageListener(PageListener pageListener) {
        mPageListener = pageListener;
    }

    public interface PageListener {
        /**
         * 页面总数量变化
         *
         * @param pageSize 页面总数
         */
        void onPageSizeChanged(int pageSize);

        /**
         * 页面被选中
         *
         * @param pageIndex 选中的页面
         */
        void onPageSelect(int pageIndex);
    }

    //--- 日志处理 ----------------------------------------------------------------------------------

    private void Logi(String msg) {
        if (!PagerConfig.isShowLog()) return;
        Log.i(TAG, msg);
    }

    private void Loge(String msg) {
        if (!PagerConfig.isShowLog()) return;
        Log.e(TAG, msg);
    }
}
