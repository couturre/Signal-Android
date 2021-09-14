package org.thoughtcrime.securesms.conversation.colors

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.view.View
import android.widget.EdgeEffect
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Draws the ChatColors color or gradient following this procedure:
 *
 * 1. Have the RecyclerView's ItemDecoration#onDraw method, fill the bounds of the RecyclerView with the background color or drawable
 * 2. Have each child item draw the bubble shape with the "clear" blend mode to "hole punch" a region within the background already drawn by the RecyclerView
 * 3. In the RecyclerView's ItemDecoration#onDrawOver method, draw the gradient with the full bounds of the RecyclerView using the DST_OVER blend mode. This will draw the gradient "underneath" the background rendered in step 1 however will show portions of the gradient in the areas "cleared" by the rendering in step 2
 */
class RecyclerViewColorizer(private val recyclerView: RecyclerView) {

  private var topEdgeEffect: EdgeEffect? = null
  private var bottomEdgeEffect: EdgeEffect? = null

  private fun getLayoutManager(): LinearLayoutManager = recyclerView.layoutManager as LinearLayoutManager

  private var useLayer = false

  private val noLayerXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
  private val layerXfermode = PorterDuffXfermode(PorterDuff.Mode.XOR)

  private var chatColors: ChatColors? = null

  fun setChatColors(chatColors: ChatColors) {
    this.chatColors = chatColors
    recyclerView.invalidateItemDecorations()
  }

  private val edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
    override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
      val edgeEffect = super.createEdgeEffect(view, direction)
      when (direction) {
        DIRECTION_TOP -> topEdgeEffect = edgeEffect
        DIRECTION_BOTTOM -> bottomEdgeEffect = edgeEffect
        DIRECTION_LEFT -> Unit
        DIRECTION_RIGHT -> Unit
      }

      return edgeEffect
    }
  }

  private val scrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      super.onScrolled(recyclerView, dx, dy)

      val firstItemPos = getLayoutManager().findFirstVisibleItemPosition()
      val lastItemPos = getLayoutManager().findLastVisibleItemPosition()
      val itemCount = getLayoutManager().itemCount
      val firstVisible = firstItemPos == 0 && itemCount >= 1
      val lastVisible = lastItemPos == itemCount - 1 && itemCount >= 1

      if (firstVisible || lastVisible || isOverscrolled()) {
        useLayer = true
        recyclerView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
      } else {
        useLayer = false
        recyclerView.setLayerType(View.LAYER_TYPE_NONE, null)
      }
    }
  }

  private val itemDecoration = object : RecyclerView.ItemDecoration() {
    private val holePunchPaint = Paint().apply {
      xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
      color = Color.BLACK
    }

    private val shaderPaint = Paint()
    private val colorPaint = Paint()

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
      outRect.setEmpty()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
      super.onDraw(c, parent, state)

      val colors = chatColors ?: return

      if (useLayer) {
        c.drawColor(Color.WHITE)
      }

      for (i in 0 until parent.childCount) {
        val child = parent.getChildAt(i)
        if (child != null && child is Colorizable) {
          child.colorizerProjections.forEach {
            c.drawPath(it.path, holePunchPaint)
          }
        }
      }

      drawShaderMask(c, parent, colors)
    }

    private fun drawShaderMask(canvas: Canvas, parent: RecyclerView, chatColors: ChatColors) {
      if (useLayer) {
        shaderPaint.xfermode = layerXfermode
        colorPaint.xfermode = layerXfermode
      } else {
        shaderPaint.xfermode = noLayerXfermode
        colorPaint.xfermode = noLayerXfermode
      }

      val shader = chatColors.asShader(0, 0, parent.width, parent.height)
      shaderPaint.shader = shader
      colorPaint.color = chatColors.asSingleColor()

      canvas.drawRect(
        0f,
        0f,
        parent.width.toFloat(),
        parent.height.toFloat(),
        if (shader == null) colorPaint else shaderPaint
      )
    }
  }

  init {
    recyclerView.edgeEffectFactory = edgeEffectFactory
    recyclerView.addOnScrollListener(scrollListener)
    recyclerView.addItemDecoration(itemDecoration)
  }

  private fun isOverscrolled(): Boolean {
    val topFinished = topEdgeEffect?.isFinished ?: true
    val bottomFinished = bottomEdgeEffect?.isFinished ?: true
    return !topFinished || !bottomFinished
  }
}