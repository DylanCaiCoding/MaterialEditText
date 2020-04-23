package com.dylanc.materialedittext

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.appcompat.widget.AppCompatEditText

class MaterialEditText2(context: Context, attrs: AttributeSet) :
  AppCompatEditText(context, attrs), View.OnFocusChangeListener {

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val path = Path()

  private val textMargin = 8.dp
  private val smallTextSize = 12.dp
  private val smallTextBaseLine = 22.dp
  private var smallTextColor = Color.parseColor("#aaaaaa")
  private var lineColor = Color.parseColor("#cccccc")
  private var lineAccentColor = 0
  private val lineMaxShakeOffset = 15.dp
  private val lineOffset = 12.dp
  private var expandState = ExpandState.NORMAL

  private lateinit var focusedAnimatorSet: AnimatorSet
  private lateinit var riseAnimatorSet: AnimatorSet

  private var textRiseFraction = 0f
    set(value) {
      field = value
      invalidate()
    }
  private var linePullDegree = 0f
    set(value) {
      field = value
      invalidate()
    }
  private var lineShakeOffset = lineMaxShakeOffset * 2
    set(value) {
      field = value
      invalidate()
    }
  private var showLineFraction = 0f
    set(value) {
      field = value
      invalidate()
    }

  init {
    paint.strokeJoin = Paint.Join.ROUND
    setHintTextColor(Color.TRANSPARENT)
    setBackgroundColor(Color.TRANSPARENT)
    setPadding(
      (paddingLeft + textMargin).toInt(),
      (paddingTop + textMargin + smallTextSize).toInt(),
      (paddingRight + textMargin).toInt(),
      (paddingBottom + lineMaxShakeOffset).toInt()
    )
    lineAccentColor = context.accentColor
    onFocusChangeListener = this

    initAnimator()
  }

  private fun initAnimator() {
    val showHighlightAnimator = ObjectAnimator.ofFloat(this, "showLineFraction", 1f)
      .apply {
        duration = 500
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationStart(animation: Animator?) {
            expandState = ExpandState.SHOW_HIGHLIGHT
          }
        })
      }
    val hideHighlightAnimator = ObjectAnimator.ofFloat(this, "showLineFraction", 1f)
      .apply {
        duration = 500
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationStart(animation: Animator?) {
            expandState = ExpandState.HIDE_HIGHLIGHT
          }
        })
      }
    val pullLineAnimator = ObjectAnimator.ofFloat(this, "linePullDegree", lineMaxShakeOffset * 2)
      .apply {
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationStart(animation: Animator?) {
            expandState = ExpandState.PULL_LINE
          }
        })
      }
    val textRiseAnimator = ObjectAnimator.ofFloat(this, "textRiseFraction", 1f)
      .apply {
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationStart(animation: Animator?) {
            expandState = ExpandState.TEXT_RISE
          }
        })
      }
    val lineShakeAnimator =
      ObjectAnimator.ofFloat(
        this, "lineShakeOffset",
        lineMaxShakeOffset * 2, (-20).dp, 0f, 8.dp, 0.dp
      ).apply { duration = 400 }
    riseAnimatorSet = AnimatorSet()
      .apply {
        playTogether(textRiseAnimator, lineShakeAnimator)
      }
    focusedAnimatorSet = AnimatorSet()
      .apply {
        playSequentially(showHighlightAnimator, pullLineAnimator,textRiseAnimator)
      }
  }

  override fun onFocusChange(v: View?, hasFocus: Boolean) {
    if (hasFocus) {
      focusedAnimatorSet.start()
    } else {

    }
  }

  override fun onDraw(canvas: Canvas?) {
    super.onDraw(canvas)
    if (canvas == null) {
      return
    }

    // 绘制 hint 文字
    paint.color = smallTextColor
    paint.style = Paint.Style.FILL
    paint.textSize = textSize - textRiseFraction * (textSize - smallTextSize)
    val textControlPotionY = when (expandState) {
      ExpandState.TEXT_RISE -> baseline + linePullDegree * (1 - textRiseFraction)
      else -> baseline + linePullDegree
    }
    val x2 = (width - paddingRight).toFloat()
    val vOffset = textRiseFraction * (smallTextBaseLine - baseline)
    path.reset()
    path.moveTo(textMargin, baseline.toFloat())
    path.quadTo(width / 2f, textControlPotionY, x2, baseline.toFloat())
    canvas.drawTextOnPath(hint.toString(), path, 0f, vOffset, paint)

    // 绘制抖动线
    paint.strokeWidth = 1.dp
    paint.style = Paint.Style.STROKE
    paint.color = when (expandState) {
      ExpandState.PULL_LINE,
      ExpandState.TEXT_RISE,
      ExpandState.EXPANDED -> lineAccentColor
      else -> lineColor
    }
    val lineY = baseline + lineOffset
    val lineControlPointY = when (expandState) {
      ExpandState.TEXT_RISE -> lineY + lineShakeOffset
      else -> lineY + linePullDegree
    }
    Log.d("test","lineControlPointY -> $lineControlPointY")
    path.reset()
    path.moveTo(textMargin, lineY)
    path.quadTo(width / 2f, lineControlPointY, (width - paddingRight).toFloat(), lineY)
    canvas.drawPath(path, paint)

    // 绘制高亮线
    val lineLength = width - textMargin - paddingRight
    paint.color = lineAccentColor
    when (expandState) {
      ExpandState.SHOW_HIGHLIGHT -> {
        canvas.drawLine(
          width / 2 - lineLength * showLineFraction / 2, lineY,
          width / 2 + lineLength * showLineFraction / 2, lineY, paint
        )
      }
      ExpandState.HIDE_HIGHLIGHT -> {
        canvas.drawLine(
          textMargin + lineLength * showLineFraction, lineY,
          width - paddingRight.toFloat(), lineY, paint
        )
      }
      else -> {
      }
    }
  }
}

enum class ExpandState {
  NORMAL, SHOW_HIGHLIGHT, PULL_LINE, TEXT_RISE, EXPANDED, HIDE_HIGHLIGHT, TEXT_DROP
}