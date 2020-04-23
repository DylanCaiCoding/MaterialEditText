package com.dylanc.materialedittext

import android.animation.*
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.animation.BounceInterpolator
import androidx.appcompat.widget.AppCompatEditText
import kotlin.properties.Delegates

/**
 * @author Dylan Cai
 * @since 2019/10/29
 */
class MaterialEditText(context: Context?, attrs: AttributeSet?) :
  AppCompatEditText(context, attrs) {

  companion object {
    private var focusedAnimPlaying: Boolean by Delegates.observable(false,
      { _, _, new ->
        for (listener in animationListeners) {
          listener.invoke(new)
        }
      })
    private val animationListeners = mutableListOf<(playing: Boolean) -> Unit>()
  }

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
  private var showLineFraction = 0f
    set(value) {
      field = value
      invalidate()
    }
  private var hideLineFraction = 0f
    set(value) {
      field = value
      invalidate()
    }
  private var textRiseFraction = 0f
    set(value) {
      field = value
      invalidate()
    }
  private var pullDegree = 0f
    set(value) {
      field = value
      invalidate()
    }
  private var lineShakeOffset = lineMaxShakeOffset * 2
    set(value) {
      field = value
      invalidate()
    }
  private var rise = false

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
    lineAccentColor = getAccentColor()


    val showLineAnimator =
      ObjectAnimator.ofFloat(this@MaterialEditText, "showLineFraction", 1f)
        .apply { duration = 500}
    val hideLineAnimator =
      ObjectAnimator.ofFloat(this@MaterialEditText, "hideLineFraction", 1f)
        .apply { duration = 500 }
    val pullLineAnimator = ObjectAnimator.ofFloat(this, "pullDegree", lineMaxShakeOffset * 2)
    val riseAnimatorSet = AnimatorSet()
      .apply {
        val textRiseAnimator =
          ObjectAnimator.ofFloat(this@MaterialEditText, "textRiseFraction", 1f)
        val lineShakeAnimator =
          ObjectAnimator.ofFloat(
            this@MaterialEditText, "lineShakeOffset",
            lineMaxShakeOffset * 2, (-20).dp, 0f, 8.dp, 0.dp
          ).apply { duration = 400 }
        playTogether(textRiseAnimator, lineShakeAnimator)
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationStart(p0: Animator?) {
            rise = true
          }

          override fun onAnimationEnd(p0: Animator?) {
            rise = false
            pullDegree = 0f
          }
        })
      }
    val focusedAnimatorSet = AnimatorSet()
      .apply {
        playSequentially(showLineAnimator, pullLineAnimator, riseAnimatorSet)
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationStart(p0: Animator?) {
            isCursorVisible = false
            animationListeners.remove(::onFocusedAnimPlaying)
            focusedAnimPlaying = true
          }

          override fun onAnimationEnd(p0: Animator?) {
            isCursorVisible = true
            animationListeners.add(::onFocusedAnimPlaying)
            focusedAnimPlaying = false
            showKeyboard()
          }
        })
      }
    val loseFocusAnimatorSet = AnimatorSet()
      .apply {
        val textDropAnimator =
          ObjectAnimator.ofFloat(this@MaterialEditText, "textRiseFraction", 1f, 0f)
            .apply {
              interpolator = BounceInterpolator()
              duration = 600
            }
        playSequentially(hideLineAnimator, textDropAnimator)
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationStart(p0: Animator?) {
          }

          override fun onAnimationEnd(p0: Animator?) {
            textRiseFraction = 0f
            showLineFraction = 0f
            hideLineFraction = 0f
          }
        })
      }
    setOnFocusChangeListener { _, focus ->
      if (focus) {
        if (text.toString().isEmpty()) {
          focusedAnimatorSet.start()
        } else {
          hideLineFraction = 0f
          showLineAnimator.start()
        }
      } else if (!focus) {
        if (text.toString().isEmpty()) {
          loseFocusAnimatorSet.start()
        } else {
          hideLineAnimator.start()
        }
      }
    }
    animationListeners.add(::onFocusedAnimPlaying)
  }

  private fun onFocusedAnimPlaying(playing: Boolean) {
    isEnabled = !playing
  }

  override fun onDraw(canvas: Canvas?) {
    super.onDraw(canvas)
    if (canvas != null) {
      // 绘制 hint 文字
      paint.color = smallTextColor
      paint.style = Paint.Style.FILL
      paint.textSize = textSize - textRiseFraction * (textSize - smallTextSize)
      val textControlPotionY = when {
        rise -> baseline + pullDegree * (1 - textRiseFraction)
        else -> baseline + pullDegree
      }
      path.reset()
      path.moveTo(textMargin, baseline.toFloat())
      path.quadTo(
        width / 2f,
        textControlPotionY,
        (width - paddingRight).toFloat(),
        baseline.toFloat()
      )
      canvas.drawTextOnPath(
        hint.toString(),
        path,
        0f,
        textRiseFraction * (smallTextBaseLine - baseline),
        paint
      )

      // 绘制线
      paint.strokeWidth = 1.dp
      paint.style = Paint.Style.STROKE
      val lineY = baseline + lineOffset
      if (showLineFraction == 1f && hideLineFraction == 0f) {
        paint.color = lineAccentColor
      } else {
        paint.color = lineColor
      }
      val lineControlPointY = when {
        rise -> {
          lineY + lineShakeOffset
        }
        else -> lineY + pullDegree
      }
      path.reset()
      path.moveTo(textMargin, lineY)
      path.quadTo(width / 2f, lineControlPointY, (width - paddingRight).toFloat(), lineY)
      canvas.drawPath(path, paint)

      // 绘制高亮线
      val lineLength = width - textMargin - paddingRight
      paint.color = lineAccentColor
      if (showLineFraction <= 1 && showLineFraction > 0) {
        if (showLineFraction == 1f) {
          Log.d("test","id: $id, $showLineFraction , $hideLineFraction")
          if (hideLineFraction > 0) {
            canvas.drawLine(
              textMargin + lineLength * hideLineFraction, lineY,
              width - paddingRight.toFloat(), lineY, paint
            )
          }
        } else {
          canvas.drawLine(
            width / 2 - lineLength * showLineFraction / 2, lineY,
            width / 2 + lineLength * showLineFraction / 2, lineY, paint
          )
        }
      }
    }
  }

  override fun performLongClick(): Boolean {
    return try {
      super.performLongClick()
    } catch (e: NullPointerException) {
      true
    }
  }

  override fun performLongClick(x: Float, y: Float): Boolean {
    return try {
      super.performLongClick(x, y)
    } catch (e: NullPointerException) {
      true
    }
  }

  private fun getAccentColor(): Int {
    val value = TypedValue()
    context.theme.resolveAttribute(R.attr.colorAccent, value, true)
    return value.data
  }

}